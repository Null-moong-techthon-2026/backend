package kr.boothrock.api.system;

import kr.boothrock.api.config.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SystemStatusController.class)
@ActiveProfiles("test")
@Import({SecurityConfig.class, SystemStatusControllerTest.TestUsers.class})
class SystemStatusControllerTest {
    @TestConfiguration
    static class TestUsers {
        @Bean
        UserDetailsService users() {
            return new InMemoryUserDetailsManager();
        }
    }
    @Autowired
    MockMvc mvc;

    @Test
    void statusIsPublic() throws Exception {
        mvc.perform(get("/api/system/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.service").value("boothrock-api"))
                .andExpect(jsonPath("$.stage").value("foundation"));
    }

    @Test
    void anonymousBusinessRequestsAreDenied() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized());
    }

    @Test
    void evenAnAuthenticatedUserCannotAccessUnimplementedRoutes() throws Exception {
        mvc.perform(get("/api/events").with(user("test-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsafeRequestWithoutCsrfIsRejected() throws Exception {
        mvc.perform(post("/api/events").with(user("test-user")))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfDoesNotGrantBusinessPermission() throws Exception {
        mvc.perform(post("/api/events").with(user("test-user")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void docsAndOtherActuatorEndpointsAreDeniedByDefault() throws Exception {
        for (String path : new String[]{"/v3/api-docs", "/swagger-ui.html", "/actuator/env"}) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
    }
}
