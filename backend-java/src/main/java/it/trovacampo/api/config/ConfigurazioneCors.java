package it.trovacampo.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * L'app Ionic gira su un'origine diversa (localhost:8100 in sviluppo,
 * capacitor://localhost sul dispositivo), quindi l'API deve accettarne le
 * richieste. Le origini permesse sono configurabili con
 * {@code trovacampo.cors.origini}.
 */
@EnableConfigurationProperties(ProprietaCors.class)
@Configuration
public class ConfigurazioneCors implements WebMvcConfigurer {

    private final String[] origini;

    public ConfigurazioneCors(ProprietaCors proprieta) {
        this.origini = proprieta.origini().toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(origini)
                // PUT e DELETE servono all'amministrazione delle schede
                // (/api/admin/societa): senza, il browser si ferma alla
                // preflight e la modifica fallisce con 403.
                .allowedMethods("GET", "POST", "PUT", "DELETE");
    }
}
