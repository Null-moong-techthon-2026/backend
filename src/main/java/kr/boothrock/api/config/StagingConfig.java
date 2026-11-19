package kr.boothrock.api.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Configuration
@Profile("staging")
public class StagingConfig {
    public StagingConfig(Environment environment) {
        if (environment.acceptsProfiles(Profiles.of("local | deploy"))) {
            throw new IllegalStateException("staging must not be combined with local or deploy");
        }
    }
}
