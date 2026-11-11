package com.tesoro.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

    @Bean
    OpenAPI tesoroOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Tesoro API")
                        .version("v1")
                        .description("Billetera digital en ARS y USD con ledger de doble partida. "
                                + "Las operaciones que mueven dinero requieren el header Idempotency-Key.")
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .components(new Components()
                        .addSecuritySchemes(
                                "bearer",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearer"))
                // Display order in Swagger UI
                .addTagsItem(new Tag().name("Auth"))
                .addTagsItem(new Tag().name("Operaciones"))
                .addTagsItem(new Tag().name("Cuentas"))
                .addTagsItem(new Tag().name("Administración"));
    }
}
