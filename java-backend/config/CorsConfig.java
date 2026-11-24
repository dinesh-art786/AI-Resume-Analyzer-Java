
package resume_analyzer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

@Configuration
public class CorsConfig {

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public CorsFilter corsFilter() {

        CorsConfiguration config = new CorsConfiguration();

        // Allow your Vercel frontend
        config.setAllowedOrigins(Arrays.asList(
            "https://ai-resume-analyzer-2-eight.vercel.app"
        ));

        // Allow frontend request methods
        config.setAllowedMethods(Arrays.asList(
            "GET",
            "POST",
            "PUT",
            "DELETE",
            "OPTIONS",
            "PATCH"
        ));

        // Allow request headers
        config.addAllowedHeader("*");

        // Allow credentials and authentication cookies
        config.setAllowCredentials(true);

        // Allow frontend to read Authorization header
        config.setExposedHeaders(Arrays.asList(
            "Authorization"
        ));

        UrlBasedCorsConfigurationSource source =
            new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return new CorsFilter(source);
    }
}
