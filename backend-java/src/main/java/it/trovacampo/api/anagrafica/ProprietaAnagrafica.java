package it.trovacampo.api.anagrafica;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Dove sta l'anagrafica di presenze ({@code trovacampo.anagrafica.url}). */
@ConfigurationProperties("trovacampo.anagrafica")
@Validated
public record ProprietaAnagrafica(@NotBlank String url) {}
