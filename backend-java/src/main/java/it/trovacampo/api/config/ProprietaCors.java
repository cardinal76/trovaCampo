package it.trovacampo.api.config;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Le origini da cui il browser puo' chiamare l'API ({@code trovacampo.cors.origini}). */
@ConfigurationProperties("trovacampo.cors")
@Validated
public record ProprietaCors(@NotEmpty List<String> origini) {}
