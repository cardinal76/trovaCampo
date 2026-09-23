package it.trovacampo.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class ConfigurazioneSicurezzaTest {

    private static Jwt token(Map<String, Object> claims) {
        Jwt.Builder builder =
                Jwt.withTokenValue("t")
                        .header("alg", "RS256")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(builder::claim);
        return builder.build();
    }

    @Test
    void iRuoliDiRealmDiventanoAutorita() {
        Jwt jwt = token(Map.of("realm_access", Map.of("roles", List.of("trovacampo-admin", "allenatore"))));

        assertThat(ConfigurazioneSicurezza.ruoliDiRealm(jwt))
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_trovacampo-admin", "ROLE_allenatore");
    }

    @Test
    void unTokenSenzaRuoliNonHaAutorita() {
        assertThat(ConfigurazioneSicurezza.ruoliDiRealm(token(Map.of("sub", "x")))).isEmpty();
    }

    @Test
    void accettaSoloITokenEmessiPerTrovaCampo() {
        var validatore = ConfigurazioneSicurezza.clientAutorizzato("trovacampo-frontend");

        assertThat(validatore.validate(token(Map.of("azp", "trovacampo-frontend"))).hasErrors())
                .isFalse();
        assertThat(validatore.validate(token(Map.of("azp", "presenze-frontend"))).hasErrors())
                .isTrue();
        assertThat(validatore.validate(token(Map.of("sub", "x"))).hasErrors()).isTrue();
    }
}
