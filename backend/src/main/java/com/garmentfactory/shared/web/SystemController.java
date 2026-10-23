package com.garmentfactory.shared.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, side-effect-free endpoint used by the frontend and by smoke tests. */
@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System", description = "Operational endpoints")
public class SystemController {

    private final ObjectProvider<BuildProperties> buildProperties;
    private final Environment environment;
    private final String applicationName;

    public SystemController(ObjectProvider<BuildProperties> buildProperties,
                            Environment environment,
                            @Value("${spring.application.name}") String applicationName) {
        this.buildProperties = buildProperties;
        this.environment = environment;
        this.applicationName = applicationName;
    }

    @GetMapping("/ping")
    @Operation(summary = "Application name, version, active profile and server time")
    public PingResponse ping() {
        BuildProperties build = buildProperties.getIfAvailable();
        String version = build != null ? build.getVersion() : "dev";
        String[] profiles = environment.getActiveProfiles();
        String profile = profiles.length == 0 ? "default" : String.join(",", profiles);
        return new PingResponse(applicationName, version, profile, Instant.now());
    }

    public record PingResponse(String application, String version, String profile, Instant serverTime) {
    }
}