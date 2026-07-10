package us.dot.its.jpo.timvalidator.api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import us.dot.its.jpo.timvalidator.service.TimValidationService;

@Configuration
public class TimValidatorConfiguration {

    @Bean
    public TimValidationService timValidationService() {
        return new TimValidationService();
    }
}
