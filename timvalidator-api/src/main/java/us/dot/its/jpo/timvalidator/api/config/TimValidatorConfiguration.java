package us.dot.its.jpo.timvalidator.api.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import us.dot.its.jpo.timvalidator.road.OverpassRoadGeometryProvider;
import us.dot.its.jpo.timvalidator.service.TimValidationService;

@Configuration
public class TimValidatorConfiguration {

    @Bean
    public TimValidationService timValidationService(
            @Value("${timvalidator.road-geometry.enabled:true}") boolean enabled,
            @Value("${timvalidator.road-geometry.overpass-url:https://overpass-api.de/api/interpreter}")
                    String overpassUrl,
            @Value("${timvalidator.road-geometry.query-timeout:${timvalidator.road-geometry.timeout:5s}}")
                    Duration queryTimeout,
            @Value("${timvalidator.road-geometry.http-timeout:${timvalidator.road-geometry.timeout:20s}}")
                    Duration httpTimeout,
            @Value("${timvalidator.road-geometry.search-radius-meters:30}") double searchRadiusMeters,
            @Value("${timvalidator.road-geometry.candidate-distance-tolerance-meters:8}")
                    double candidateDistanceToleranceMeters,
            @Value("${timvalidator.road-geometry.user-agent:timvalidator-api/1.0}") String userAgent) {
        if (!enabled) {
            return new TimValidationService();
        }

        OverpassRoadGeometryProvider roadGeometryProvider =
                new OverpassRoadGeometryProvider(
                        overpassUrl,
                        queryTimeout,
                        httpTimeout,
                        userAgent);
        return new TimValidationService(
                roadGeometryProvider,
                searchRadiusMeters,
                candidateDistanceToleranceMeters);
    }
}
