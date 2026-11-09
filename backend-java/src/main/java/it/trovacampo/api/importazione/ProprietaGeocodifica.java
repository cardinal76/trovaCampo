package it.trovacampo.api.importazione;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** La geocodifica automatica ({@code trovacampo.geocodifica.*}): una coordinata ogni {@code intervallo}. */
@ConfigurationProperties("trovacampo.geocodifica")
@Validated
public record ProprietaGeocodifica(@DefaultValue("PT1.1S") Duration intervallo) {}
