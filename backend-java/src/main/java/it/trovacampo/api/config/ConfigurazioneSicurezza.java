package it.trovacampo.api.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Chi può fare cosa. L'app è pubblica: ricerca, schede e segnalazione di un
 * campo restano aperte a tutti, senza login. Solo {@code /api/admin/**}
 * (l'importazione da Excel) chiede un token del Keycloak di presenze con il
 * ruolo di realm {@value #RUOLO_AMMINISTRATORE}.
 *
 * <p>Il Keycloak è quello di presenze perché gira sullo stesso server: un
 * secondo Keycloak costerebbe mezzo giga di RAM e un'altra password di
 * amministrazione per tre persone che importano file.
 */
@Configuration
public class ConfigurazioneSicurezza {

    public static final String RUOLO_AMMINISTRATORE = "trovacampo-admin";

    @Bean
    SecurityFilterChain catenaDiSicurezza(HttpSecurity http) throws Exception {
        return http
                // API senza sessione né cookie: il CSRF non ha niente da proteggere.
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        richieste ->
                                richieste
                                        .requestMatchers("/api/admin/**")
                                        .hasRole(RUOLO_AMMINISTRATORE)
                                        .anyRequest()
                                        .permitAll())
                .oauth2ResourceServer(
                        risorse -> risorse.jwt(jwt -> jwt.jwtAuthenticationConverter(convertitore())))
                .build();
    }

    /**
     * Chiavi lette dalla rete interna di Docker, issuer controllato su quello
     * pubblico: è l'indirizzo che Keycloak scrive nei token, ed è diverso da
     * quello con cui il backend lo raggiunge.
     *
     * <p>Oltre all'issuer si controlla {@code azp}, il client che ha chiesto il
     * token: un token emesso per l'app di presenze non deve aprire
     * l'amministrazione di TrovaCampo, anche se l'utente è lo stesso.
     */
    @Bean
    JwtDecoder decodificatoreJwt(
            @Value("${trovacampo.keycloak.issuer}") String issuer,
            @Value("${trovacampo.keycloak.jwks-uri}") String jwksUri,
            @Value("${trovacampo.keycloak.client-id}") String clientId) {
        NimbusJwtDecoder decodificatore = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        decodificatore.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        JwtValidators.createDefaultWithIssuer(issuer), clientAutorizzato(clientId)));
        return decodificatore;
    }

    static OAuth2TokenValidator<Jwt> clientAutorizzato(String clientId) {
        OAuth2Error errore =
                new OAuth2Error("invalid_token", "Token emesso per un altro client", null);
        return jwt ->
                clientId.equals(jwt.getClaimAsString("azp"))
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(errore);
    }

    /** I ruoli di realm di Keycloak ({@code realm_access.roles}) diventano ROLE_*. */
    static JwtAuthenticationConverter convertitore() {
        JwtAuthenticationConverter convertitore = new JwtAuthenticationConverter();
        convertitore.setJwtGrantedAuthoritiesConverter(ConfigurazioneSicurezza::ruoliDiRealm);
        convertitore.setPrincipalClaimName("preferred_username");
        return convertitore;
    }

    static Collection<GrantedAuthority> ruoliDiRealm(Jwt jwt) {
        Map<String, Object> accesso = jwt.getClaimAsMap("realm_access");
        if (accesso == null || !(accesso.get("roles") instanceof List<?> ruoli)) {
            return List.of();
        }
        return ruoli.stream()
                .map(ruolo -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + ruolo))
                .toList();
    }
}
