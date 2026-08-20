package com.servicepilot;

import org.springframework.boot.SpringApplication;

public class TestServicePilotApplication {

	public static void main(String[] args) {
		SpringApplication.from(ServicePilotApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
