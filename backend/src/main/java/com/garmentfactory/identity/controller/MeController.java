package com.garmentfactory.identity.controller;

import com.garmentfactory.identity.dto.AuthDtos.UserSummary;
import com.garmentfactory.shared.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class MeController {

    @GetMapping("/me")
    @Operation(summary = "The signed-in user with current roles and permissions")
    public UserSummary me(@AuthenticationPrincipal AuthenticatedUser user) {
        return UserSummary.from(user);
    }
}