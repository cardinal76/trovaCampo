package it.trovacampo.api.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * L'app Ionic gira su un'origine diversa (localhost:8100 in sviluppo,
 * capacitor://localhost sul dispositivo), quindi l'API deve accettarne le
 * richieste. Le origini permesse sono configurabili con
 * {@code trovacampo.cors.origini}.
 */
@Configuration
public class ConfigurazioneCors implements WebMvcConfigurer {

    private final String[] origini;

    public ConfigurazioneCors(@Value("${trovacampo.cors.origini}") String[] origini) {
        this.origini = origini;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOriginPatterns(origini).allowedMethods("GET", "POST");
    }
}
