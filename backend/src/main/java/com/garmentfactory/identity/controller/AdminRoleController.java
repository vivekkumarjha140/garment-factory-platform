package com.garmentfactory.identity.controller;

import com.garmentfactory.identity.dto.AdminDtos.PermissionResponse;
import com.garmentfactory.identity.dto.AdminDtos.RoleResponse;
import com.garmentfactory.identity.dto.AdminDtos.UpdatePermissionsRequest;
import com.garmentfactory.identity.service.RoleAdminService;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin: roles")
public class AdminRoleController {

    private final RoleAdminService service;

    public AdminRoleController(RoleAdminService service) {
        this.service = service;
    }

    /** Needed by user-management screens to offer a role picker, hence USER_VIEW. */
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public List<RoleResponse> roles() {
        return service.listRoles();
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public List<PermissionResponse> permissions() {
        return service.listPermissions();
    }

    @PutMapping("/roles/{code}/permissions")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public RoleResponse updatePermissions(@AuthenticationPrincipal AuthenticatedUser actor,
                                          @PathVariable String code,
                                          @Valid @RequestBody UpdatePermissionsRequest request) {
        return service.updatePermissions(actor, code, request.permissions());
    }
}