package com.garmentfactory.identity.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.identity.dto.AdminDtos.AdminUserResponse;
import com.garmentfactory.identity.dto.AdminDtos.CreateStaffRequest;
import com.garmentfactory.identity.entity.Role;
import com.garmentfactory.identity.entity.TokenPurpose;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.RoleRepository;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.notification.api.EmailSender;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.AuthenticatedUser;
import com.garmentfactory.shared.security.Tokens;
import com.garmentfactory.shared.util.AfterCommit;
import com.garmentfactory.shared.util.Emails;
import com.garmentfactory.shared.web.PageResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Staff-facing user administration. The controller checks the permission; the rules here protect
 * against self-lockout, privilege escalation to ADMIN and removal of the last administrator.
 */
@Service
public class AdminUserService {

    private static final String ADMIN = "ADMIN";
    private static final Duration INVITATION_TTL = Duration.ofHours(48);
    private static final int MAX_QUERY_LENGTH = 100;

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final VerificationTokenService verificationTokens;
    private final RefreshTokenService refreshTokens;
    private final LastAdminGuard lastAdminGuard;
    private final EmailSender emailSender;
    private final AuthEmails emails;
    private final AuditRecorder audit;

    public AdminUserService(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                            VerificationTokenService verificationTokens, RefreshTokenService refreshTokens,
                            LastAdminGuard lastAdminGuard, EmailSender emailSender, AuthEmails emails,
                            AuditRecorder audit) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.verificationTokens = verificationTokens;
        this.refreshTokens = refreshTokens;
        this.lastAdminGuard = lastAdminGuard;
        this.emailSender = emailSender;
        this.emails = emails;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> search(String query, String status, String role, Pageable pageable) {
        Set<UserStatus> statuses = (status == null || status.isBlank())
                ? EnumSet.allOf(UserStatus.class)
                : EnumSet.of(parseStatus(status));
        String text = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (text.length() > MAX_QUERY_LENGTH) {
            text = text.substring(0, MAX_QUERY_LENGTH);
        }
        String roleCode = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);

        Page<User> page = users.search(statuses, "%" + text + "%", roleCode.isEmpty(), roleCode, pageable);
        Map<Long, Set<String>> rolesByUser = rolesByUser(page.getContent());
        return PageResponse.from(page.map(user ->
                toResponse(user, rolesByUser.getOrDefault(user.getId(), Set.of()))));
    }

    @Transactional(readOnly = true)
    public AdminUserResponse get(String publicId) {
        User user = find(publicId);
        return toResponse(user, currentRoleCodes(user.getId()));
    }

    /** Creates an invited staff account: unusable random password plus a 48-hour set-password link. */
    @Transactional
    public AdminUserResponse createStaff(AuthenticatedUser actor, CreateStaffRequest request) {
        String email = Emails.normalize(request.email());
        Set<Role> assigned = resolveRoles(request.roles());
        Set<String> assignedCodes = codes(assigned);
        requireAdminForAdminRole(actor, Set.of(), assignedCodes);
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "An account with this email already exists.");
        }

        User user = new User();
        user.setEmail(email);
        user.setFullName(request.fullName().trim());
        user.setPasswordHash(passwordEncoder.encode(Tokens.newOpaqueToken()));
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.getRoles().addAll(assigned);
        users.save(user);

        VerificationTokenService.Issued issued =
                verificationTokens.issue(user, TokenPurpose.PASSWORD_RESET, INVITATION_TTL);
        String name = user.getFullName();
        AfterCommit.run(() -> emailSender.send(emails.staffInvitation(email, name, issued.rawToken())));
        audit.record(AuditEvent.of("STAFF_USER_CREATED", "USER", user.getPublicId(), "roles=" + assignedCodes));
        return toResponse(user, assignedCodes);
    }

    @Transactional
    public AdminUserResponse updateRoles(AuthenticatedUser actor, String publicId, Set<String> requestedRoles) {
        User user = find(publicId);
        forbidSelf(actor, user);
        Set<Role> wanted = resolveRoles(requestedRoles);
        Set<String> before = currentRoleCodes(user.getId());
        Set<String> after = codes(wanted);
        requireAdminForAdminRole(actor, before, after);

        if (before.contains(ADMIN) && !after.contains(ADMIN) && user.getStatus() == UserStatus.ACTIVE) {
            lastAdminGuard.requireAnotherActiveAdmin(user.getId());
        }
        user.getRoles().clear();
        user.getRoles().addAll(wanted);
        audit.record(AuditEvent.of("USER_ROLES_CHANGED", "USER", user.getPublicId(), "from " + before + " to " + after));
        return toResponse(user, after);
    }

    @Transactional
    public AdminUserResponse block(AuthenticatedUser actor, String publicId, String reason) {
        User user = find(publicId);
        forbidSelf(actor, user);
        Set<String> roleCodes = currentRoleCodes(user.getId());
        requireAdminForAdminRole(actor, roleCodes, Set.of());

        if (user.getStatus() != UserStatus.BLOCKED) {
            if (roleCodes.contains(ADMIN) && user.getStatus() == UserStatus.ACTIVE) {
                lastAdminGuard.requireAnotherActiveAdmin(user.getId());
            }
            user.setStatus(UserStatus.BLOCKED);
            // Flushes the status change, then ends every session. The access token dies at once because
            // authorities are re-read from the database on each request.
            refreshTokens.revokeAllForUser(user.getId());
            audit.record(AuditEvent.of("USER_BLOCKED", "USER", user.getPublicId(),
                    reason == null || reason.isBlank() ? "No reason given" : "reason: " + reason.trim()));
        }
        return toResponse(user, roleCodes);
    }

    @Transactional
    public AdminUserResponse unblock(AuthenticatedUser actor, String publicId) {
        User user = find(publicId);
        forbidSelf(actor, user);
        Set<String> roleCodes = currentRoleCodes(user.getId());
        requireAdminForAdminRole(actor, roleCodes, Set.of());
        if (user.getStatus() != UserStatus.BLOCKED) {
            throw ApiException.conflict("NOT_BLOCKED", "This account is not blocked.");
        }
        // An address that was never verified must still be verified.
        user.setStatus(user.getEmailVerifiedAt() != null ? UserStatus.ACTIVE : UserStatus.PENDING_VERIFICATION);
        users.clearLockout(user.getId());
        audit.record(AuditEvent.of("USER_UNBLOCKED", "USER", user.getPublicId(), "Account unblocked"));
        return toResponse(user, roleCodes);
    }

    private User find(String publicId) {
        return users.findByPublicId(publicId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found."));
    }

    private static void forbidSelf(AuthenticatedUser actor, User target) {
        if (actor.id() == target.getId()) {
            throw ApiException.conflict("CANNOT_MODIFY_SELF", "You cannot change your own account here.");
        }
    }

    /** Only an administrator may give, take away, block or unblock the ADMIN role. */
    private static void requireAdminForAdminRole(AuthenticatedUser actor, Set<String> before, Set<String> after) {
        boolean touchesAdmin = before.contains(ADMIN) != after.contains(ADMIN);
        if (touchesAdmin && !actor.roles().contains(ADMIN)) {
            throw ApiException.forbidden("ADMIN_ROLE_REQUIRED", "Only an administrator can change administrator accounts.");
        }
    }

    private Set<Role> resolveRoles(Set<String> requested) {
        Set<String> wanted = requested.stream()
                .map(code -> code.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        List<Role> found = roles.findByCodeIn(wanted);
        Set<String> foundCodes = found.stream().map(Role::getCode).collect(Collectors.toSet());
        if (!foundCodes.containsAll(wanted)) {
            wanted.removeAll(foundCodes);
            throw ApiException.badRequest("UNKNOWN_ROLE", "Unknown role(s): " + wanted);
        }
        return new HashSet<>(found);
    }

    private static Set<String> codes(Collection<Role> roleList) {
        return roleList.stream().map(Role::getCode).collect(Collectors.toCollection(TreeSet::new));
    }

    private Set<String> currentRoleCodes(Long userId) {
        return new TreeSet<>(users.findRoleCodes(userId));
    }

    private Map<Long, Set<String>> rolesByUser(Collection<User> page) {
        if (page.isEmpty()) {
            return Map.of();
        }
        List<Long> ids = page.stream().map(User::getId).toList();
        Map<Long, Set<String>> result = new HashMap<>();
        for (Object[] row : users.findRoleCodesForUsers(ids)) {
            result.computeIfAbsent((Long) row[0], key -> new TreeSet<>()).add((String) row[1]);
        }
        return result;
    }

    private static UserStatus parseStatus(String status) {
        try {
            return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw ApiException.badRequest("INVALID_STATUS", "Status must be one of " + List.of(UserStatus.values()));
        }
    }

    private static AdminUserResponse toResponse(User user, Set<String> roleCodes) {
        return new AdminUserResponse(user.getPublicId(), user.getEmail(), user.getFullName(), user.getPhone(),
                user.getStatus().name(), roleCodes, user.getEmailVerifiedAt(), user.getLastLoginAt(), user.getCreatedAt());
    }
}