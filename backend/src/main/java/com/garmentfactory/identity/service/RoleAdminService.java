package com.garmentfactory.identity.service;

import com.garmentfactory.audit.api.AuditEvent;
import com.garmentfactory.audit.api.AuditRecorder;
import com.garmentfactory.identity.dto.AdminDtos.PermissionResponse;
import com.garmentfactory.identity.dto.AdminDtos.RoleResponse;
import com.garmentfactory.identity.entity.Permission;
import com.garmentfactory.identity.entity.Role;
import com.garmentfactory.identity.repository.PermissionRepository;
import com.garmentfactory.identity.repository.RoleRepository;
import com.garmentfactory.shared.exception.ApiException;
import com.garmentfactory.shared.security.AuthenticatedUser;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Edits which permissions a role carries. Changes apply to the next request of every holder. */
@Service
public class RoleAdminService {

    /** ADMIN is the super role; CUSTOMER must never gain staff permissions, since every customer has it. */
    private static final Set<String> LOCKED_ROLES = Set.of("ADMIN", "CUSTOMER");

    private final RoleRepository roles;
    private final PermissionRepository permissions;
    private final AuditRecorder audit;

    public RoleAdminService(RoleRepository roles, PermissionRepository permissions, AuditRecorder audit) {
        this.roles = roles;
        this.permissions = permissions;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> listRoles() {
        return roles.findAllWithPermissions().stream().map(RoleAdminService::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<PermissionResponse> listPermissions() {
        return permissions.findAllByOrderByCodeAsc().stream()
                .map(permission -> new PermissionResponse(permission.getCode(), permission.getDescription()))
                .toList();
    }

    @Transactional
    public RoleResponse updatePermissions(AuthenticatedUser actor, String roleCode, Set<String> requested) {
        String code = roleCode.trim().toUpperCase(Locale.ROOT);
        Role role = roles.findByCode(code)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ROLE_NOT_FOUND", "Role not found."));
        if (LOCKED_ROLES.contains(code)) {
            throw ApiException.conflict("ROLE_IMMUTABLE", "The " + code + " role cannot be edited.");
        }

        Set<String> wanted = requested.stream()
                .map(permission -> permission.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toCollection(TreeSet::new));
        List<Permission> found = wanted.isEmpty() ? List.of() : permissions.findByCodeIn(wanted);
        Set<String> foundCodes = found.stream().map(Permission::getCode).collect(Collectors.toSet());
        if (!foundCodes.containsAll(wanted)) {
            wanted.removeAll(foundCodes);
            throw ApiException.badRequest("UNKNOWN_PERMISSION", "Unknown permission(s): " + wanted);
        }

        Set<String> before = role.getPermissions().stream()
                .map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new));
        Set<String> after = new TreeSet<>(foundCodes);

        // A non-administrator may only hand out permissions they hold themselves.
        if (!actor.roles().contains("ADMIN")) {
            Set<String> added = new TreeSet<>(after);
            added.removeAll(before);
            if (!actor.permissions().containsAll(added)) {
                throw ApiException.forbidden("PERMISSION_ESCALATION", "You can only grant permissions you hold yourself.");
            }
        }

        role.getPermissions().clear();
        role.getPermissions().addAll(found);
        audit.record(AuditEvent.of("ROLE_PERMISSIONS_CHANGED", "ROLE", code, "from " + before + " to " + after));
        return new RoleResponse(role.getCode(), role.getName(), role.getDescription(), role.isSystem(), after);
    }

    private static RoleResponse toResponse(Role role) {
        Set<String> codes = role.getPermissions().stream()
                .map(Permission::getCode).collect(Collectors.toCollection(TreeSet::new));
        return new RoleResponse(role.getCode(), role.getName(), role.getDescription(), role.isSystem(), codes);
    }
}