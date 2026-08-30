package com.servicepilot;

import com.servicepilot.config.AdminSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AdminSecurityProperties.class)
public class ServicePilotApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServicePilotApplication.class, args);
	}

}
