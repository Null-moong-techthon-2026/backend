package kr.boothrock.api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openAPI() {
        return new OpenAPI().info(new Info()
                .title("Boothrock API")
                .version("0.0.1")
                .description("Local PostgreSQL-backed prototype: authentication, events, recruitment, applications, booths, operations, maps, media, and announcements. Business routes are not enabled in the deploy profile."));
    }

    @Bean
    OperationCustomizer csrfHeaderForChanges() {
        return (operation, handler) -> {
            var method = handler.getMethod();
            boolean changesData = method.isAnnotationPresent(PostMapping.class)
                    || method.isAnnotationPresent(PutMapping.class)
                    || method.isAnnotationPresent(PatchMapping.class)
                    || method.isAnnotationPresent(DeleteMapping.class);
            if (!changesData || !handler.getBeanType().getPackageName().startsWith("kr.boothrock.api")) {
                return operation;
            }
            boolean documented = operation.getParameters() != null && operation.getParameters().stream()
                    .anyMatch(parameter -> "header".equals(parameter.getIn())
                            && "X-CSRF-TOKEN".equalsIgnoreCase(parameter.getName()));
            if (!documented) {
                operation.addParametersItem(new Parameter().name("X-CSRF-TOKEN").in("header")
                        .required(true).description("GET /api/auth/csrf token; refresh after login"));
            }
            return operation;
        };
    }
}
