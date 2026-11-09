package it.trovacampo.api.sicurezza;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Il Keycloak di presenze ({@code trovacampo.keycloak.*}): l'issuer scritto nei
 * token (indirizzo pubblico), da dove leggere le chiavi (in produzione la rete
 * interna di Docker) e il client per cui i token devono essere emessi.
 */
@ConfigurationProperties("trovacampo.keycloak")
@Validated
public record ProprietaKeycloak(
        @NotBlank String issuer, @NotBlank String jwksUri, @NotBlank String clientId) {}
