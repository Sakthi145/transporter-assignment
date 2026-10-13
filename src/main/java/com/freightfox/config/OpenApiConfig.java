package com.freightfox.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata shown in Swagger UI.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI transporterAssignmentOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Transporter Assignment on Lanes API")
                .version("1.0.0")
                .description("Submit lanes and transporter quotes, then compute the minimum-cost assignment "
                        + "of transporters to lanes using at most a given number of transporters."));
    }
}
