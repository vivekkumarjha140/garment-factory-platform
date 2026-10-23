package com.garmentfactory.identity.controller;

import com.garmentfactory.identity.dto.AdminDtos.AdminUserResponse;
import com.garmentfactory.identity.dto.AdminDtos.BlockRequest;
import com.garmentfactory.identity.dto.AdminDtos.CreateStaffRequest;
import com.garmentfactory.identity.dto.AdminDtos.UpdateRolesRequest;
import com.garmentfactory.identity.service.AdminUserService;
import com.garmentfactory.shared.security.AuthenticatedUser;
import com.garmentfactory.shared.web.PageResponse;
import com.garmentfactory.shared.web.Paging;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/users")
@Tag(name = "Admin: users")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public PageResponse<AdminUserResponse> search(@RequestParam(required = false) String q,
                                                  @RequestParam(required = false) String status,
                                                  @RequestParam(required = false) String role,
                                                  @RequestParam(defaultValue = "0") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return service.search(q, status, role, Paging.of(page, size));
    }

    @GetMapping("/{publicId}")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public AdminUserResponse get(@PathVariable String publicId) {
        return service.get(publicId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public AdminUserResponse createStaff(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @Valid @RequestBody CreateStaffRequest request) {
        return service.createStaff(actor, request);
    }

    @PutMapping("/{publicId}/roles")
    @PreAuthorize("hasAuthority('USER_MANAGE')")
    public AdminUserResponse updateRoles(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable String publicId,
                                         @Valid @RequestBody UpdateRolesRequest request) {
        return service.updateRoles(actor, publicId, request.roles());
    }

    @PostMapping("/{publicId}/block")
    @PreAuthorize("hasAuthority('USER_BLOCK')")
    public AdminUserResponse block(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @PathVariable String publicId,
                                   @Valid @RequestBody(required = false) BlockRequest request) {
        return service.block(actor, publicId, request == null ? null : request.reason());
    }

    @PostMapping("/{publicId}/unblock")
    @PreAuthorize("hasAuthority('USER_BLOCK')")
    public AdminUserResponse unblock(@AuthenticationPrincipal AuthenticatedUser actor, @PathVariable String publicId) {
        return service.unblock(actor, publicId);
    }
}