package com.garmentfactory.identity.service;

import com.garmentfactory.identity.entity.Role;
import com.garmentfactory.identity.entity.User;
import com.garmentfactory.identity.entity.UserStatus;
import com.garmentfactory.identity.repository.RoleRepository;
import com.garmentfactory.identity.repository.UserRepository;
import com.garmentfactory.shared.config.AppProperties;
import com.garmentfactory.shared.util.Emails;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first administrator from BOOTSTRAP_ADMIN_* variables when no ADMIN exists yet.
 * The password is never logged. Remove the variables after the first start.
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final AppProperties properties;
    private final Clock clock;

    public AdminBootstrap(UserRepository users, RoleRepository roles, PasswordEncoder passwordEncoder,
                          PasswordPolicy passwordPolicy, AppProperties properties, Clock clock) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.Bootstrap config = properties.bootstrap();
        if (config == null || isBlank(config.adminEmail()) || isBlank(config.adminPassword())) {
            return;
        }
        if (users.countWithRole("ADMIN") > 0) {
            log.info("Bootstrap administrator skipped: an administrator already exists");
            return;
        }
        String email = Emails.normalize(config.adminEmail());
        if (users.findByEmail(email).isPresent()) {
            log.warn("Bootstrap administrator skipped: the configured email already belongs to a non-admin account");
            return;
        }

        passwordPolicy.validate(config.adminPassword(), email);
        Role admin = roles.findByCode("ADMIN")
                .orElseThrow(() -> new IllegalStateException("Seed role ADMIN is missing"));

        User user = new User();
        user.setEmail(email);
        user.setFullName(isBlank(config.adminName()) ? "Administrator" : config.adminName().trim());
        user.setPasswordHash(passwordEncoder.encode(config.adminPassword()));
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(clock.instant());
        user.getRoles().add(admin);
        users.save(user);
        log.info("Bootstrap administrator created");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}