package kr.boothrock.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Boothrock API")
                .version("0.0.1")
                .description("Local account signup and session authentication backed by PostgreSQL. Event and booth APIs are not implemented yet."));
    }
}
