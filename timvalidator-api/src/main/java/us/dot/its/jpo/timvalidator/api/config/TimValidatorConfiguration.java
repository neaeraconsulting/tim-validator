package us.dot.its.jpo.timvalidator.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import us.dot.its.jpo.timvalidator.service.TimValidationService;

@Configuration
@EnableConfigurationProperties(RoadwayHeadingProperties.class)
public class TimValidatorConfiguration {

    @Bean
    public RoadwayHeadingPolicy roadwayHeadingPolicy(RoadwayHeadingProperties properties) {
        return new RoadwayHeadingPolicy(properties);
    }

    @Bean
    public TimValidationService timValidationService() {
        return new TimValidationService();
    }
}
