package it.trovacampo.api.indirizzi;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/** Dove prendere gli impianti del CMRC ({@code trovacampo.indirizzi.open-data.cmrc-url}). */
@ConfigurationProperties("trovacampo.indirizzi.open-data")
@Validated
public record ProprietaOpenData(@NotBlank String cmrcUrl) {}
