package com.pointbank.bankplatform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@SpringBootApplication
@OpenAPIDefinition(info = @Info(
		title = "Bank Platform API",
		version = "v1",
		description = "REST API for managing bank customers."
))
public class BankPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankPlatformApplication.class, args);
	}

}
