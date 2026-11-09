package it.trovacampo.api;

import static org.assertj.core.api.Assertions.assertThat;

import it.trovacampo.api.anagrafica.ProprietaAnagrafica;
import it.trovacampo.api.config.ProprietaCors;
import it.trovacampo.api.geocoding.ProprietaNominatim;
import it.trovacampo.api.indirizzi.ProprietaOpenData;
import it.trovacampo.api.notifiche.ProprietaNotifiche;
import it.trovacampo.api.prospect.ProprietaRasd;
import it.trovacampo.api.sicurezza.ProprietaKeycloak;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * I valori che le classi di configurazione tipizzata ricevono dal vero
 * {@code application.yml}. Prima erano {@code @Value} sparsi con il proprio
 * default: un valore che si legge male (i due punti di una finestra oraria, una
 * lista separata da virgole) non fa fallire l'avvio, e qui lo si vede.
 */
@SpringBootTest(
        properties = {
            "trovacampo.seed.abilitato=false",
            "trovacampo.geocodifica.automatica=false",
            "trovacampo.province.completamento=false",
            "trovacampo.notifiche.attive=false"
        })
class ProprietaApplicazioneTest {

    @Autowired private ProprietaRasd rasd;
    @Autowired private ProprietaNotifiche notifiche;
    @Autowired private ProprietaNominatim nominatim;
    @Autowired private ProprietaKeycloak keycloak;
    @Autowired private ProprietaAnagrafica anagrafica;
    @Autowired private ProprietaOpenData openData;
    @Autowired private ProprietaCors cors;

    @Test
    void ilRegistroRasdHaIlRitmoPrudenteDiDefault() {
        assertThat(rasd.url()).startsWith("https://");
        assertThat(rasd.intervalloMs()).isEqualTo(1100);
        assertThat(rasd.dettaglioValidoGiorni()).isEqualTo(30);
        assertThat(rasd.ritmo().pausaMs()).isEqualTo(3000);
        assertThat(rasd.ritmo().jitterMs()).isEqualTo(1000);
        assertThat(rasd.ritmo().finestra()).isEqualTo("22:00-07:00");
        assertThat(rasd.ritmo().fuso()).isEqualTo("Europe/Rome");
        assertThat(rasd.ritmo().tettoGiornaliero()).isEqualTo(7000);
    }

    @Test
    void leNotificheMandanoSoloAgliHostDelleListe() {
        assertThat(notifiche.serviziPush())
                .containsExactly(
                        "fcm.googleapis.com",
                        "android.googleapis.com",
                        "push.services.mozilla.com",
                        "push.apple.com",
                        "notify.windows.com");
        assertThat(notifiche.contatto()).isEqualTo("https://trovacampo.footballer.it");
        assertThat(notifiche.vapid().pubblica()).isEmpty();
        assertThat(notifiche.vapid().privata()).isEmpty();
    }

    @Test
    void nominatimVaAUnaRichiestaOgniSecondoEUnPoco() {
        assertThat(nominatim.intervallo()).isEqualTo(Duration.ofMillis(1100));
        assertThat(nominatim.url()).isEqualTo("https://nominatim.openstreetmap.org");
        assertThat(nominatim.userAgent()).startsWith("TrovaCampo/");
    }

    @Test
    void keycloakAccettaSoloIlClientDiTrovaCampo() {
        assertThat(keycloak.clientId()).isEqualTo("trovacampo-frontend");
        assertThat(keycloak.issuer()).endsWith("/realms/presenze");
        assertThat(keycloak.jwksUri()).endsWith("/protocol/openid-connect/certs");
    }

    @Test
    void ilResto() {
        assertThat(anagrafica.url()).isNotBlank();
        assertThat(openData.cmrcUrl()).isNotBlank();
        assertThat(cors.origini()).containsExactly("*");
    }
}
