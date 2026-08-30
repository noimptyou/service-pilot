package com.servicepilot.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "service-pilot.security.admin")
public record AdminSecurityProperties(
        @NotBlank String username,
        @NotBlank String password
) {
}
