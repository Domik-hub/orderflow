package dev.domikhub.orderflow.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI orderflowApi() {
        return new OpenAPI().info(new Info().title("OrderFlow API").version("1.0.0")
                .description("Product catalogue, carts and transactional checkout in KZT. Register, log in and use Authorize. Checkout requires an Idempotency-Key UUID header. Demo credentials are documented in README."))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
