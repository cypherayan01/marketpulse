package com.marketpulse.dashboard;

import com.marketpulse.config.MarketPulseProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Lets the dashboard on Vercel (a different origin) call /api/** - and nobody else. */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final MarketPulseProperties props;

    public WebConfig(MarketPulseProperties props) {
        this.props = props;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(props.dashboard().allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST")
                .allowedHeaders("Authorization", "Content-Type");
    }
}
