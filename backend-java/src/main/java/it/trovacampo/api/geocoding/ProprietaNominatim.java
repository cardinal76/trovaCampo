package it.trovacampo.api.geocoding;

import jakarta.validation.constraints.NotBlank;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Come parlare con Nominatim ({@code trovacampo.nominatim.*}): l'indirizzo, chi
 * siamo (l'usage policy chiede uno User-Agent vero) e l'intervallo minimo fra due
 * richieste da qualsiasi parte del backend (al massimo una al secondo).
 */
@ConfigurationProperties("trovacampo.nominatim")
@Validated
public record ProprietaNominatim(
        @NotBlank String url,
        @NotBlank String userAgent,
        @DefaultValue("PT1.1S") Duration intervallo) {}
