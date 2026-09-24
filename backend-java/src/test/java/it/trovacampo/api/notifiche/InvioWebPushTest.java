package it.trovacampo.api.notifiche;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Contro un servizio push finto, in locale: quello che riceve è quello che riceverebbe Google. */
class InvioWebPushTest {

    private record Ricevuta(String metodo, Map<String, List<String>> intestazioni, byte[] corpo) {}

    private HttpServer servizio;
    private final AtomicReference<Ricevuta> ricevuta = new AtomicReference<>();
    private final AtomicReference<Integer> risposta = new AtomicReference<>(201);

    private final ChiaviVapid vapid = ChiaviVapid.nuove();
    private final Browser browser = new Browser();
    private InvioWebPush invio;

    @BeforeEach
    void avvia() throws IOException {
        servizio = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servizio.createContext(
                "/",
                scambio -> {
                    ricevuta.set(
                            new Ricevuta(
                                    scambio.getRequestMethod(),
                                    Map.copyOf(scambio.getRequestHeaders()),
                                    scambio.getRequestBody().readAllBytes()));
                    scambio.sendResponseHeaders(risposta.get(), -1);
                    scambio.close();
                });
        servizio.start();

        DepositoChiaviVapid deposito = mock(DepositoChiaviVapid.class);
        when(deposito.chiavi()).thenReturn(vapid);
        invio =
                new InvioWebPush(
                        HttpClient.newHttpClient(),
                        deposito,
                        "https://trovacampo.footballer.it",
                        Clock.fixed(Instant.parse("2026-09-06T08:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void ferma() {
        servizio.stop(0);
    }

    private Iscrizione iscrizione() {
        String endpoint = "http://127.0.0.1:" + servizio.getAddress().getPort() + "/fcm/send/abc123";
        return new Iscrizione(
                Iscrizione.idDi(endpoint), endpoint, browser.p256dh(), browser.auth(), List.of(), true, false,
                null, null, null, null, Instant.now());
    }

    private String intestazione(String nome) {
        return ricevuta.get().intestazioni().entrySet().stream()
                .filter(voce -> voce.getKey().equalsIgnoreCase(nome))
                .map(voce -> voce.getValue().getFirst())
                .findFirst()
                .orElse(null);
    }

    @Test
    void mandaLaNotificaCifrataEFirmata() {
        String notifica = "{\"titolo\":\"Tra un'ora: BOREALE – VIGOR PERCONTI\",\"url\":\"/societa/1\"}";

        InvioWebPush.Esito esito = invio.invia(iscrizione(), notifica, Duration.ofMinutes(60));

        assertThat(esito).isEqualTo(InvioWebPush.Esito.CONSEGNATA);
        assertThat(ricevuta.get().metodo()).isEqualTo("POST");
        assertThat(intestazione("Content-Encoding")).isEqualTo("aes128gcm");
        assertThat(intestazione("TTL")).isEqualTo("3600");
        assertThat(intestazione("Urgency")).isEqualTo("high");
        assertThat(intestazione("Authorization")).startsWith("vapid t=").endsWith("k=" + vapid.pubblicaBase64());
        // Il servizio push non legge niente; il browser sì.
        assertThat(new String(ricevuta.get().corpo())).doesNotContain("BOREALE");
        assertThat(browser.decifra(ricevuta.get().corpo())).isEqualTo(notifica);
    }

    @Test
    void unIscrizioneSparitaRisultaScaduta() {
        risposta.set(410);
        assertThat(invio.invia(iscrizione(), "{}", Duration.ofMinutes(30))).isEqualTo(InvioWebPush.Esito.SCADUTA);

        risposta.set(404);
        assertThat(invio.invia(iscrizione(), "{}", Duration.ofMinutes(30))).isEqualTo(InvioWebPush.Esito.SCADUTA);
    }

    @Test
    void gliAltriRifiutiSiRiprovano() {
        risposta.set(503);
        assertThat(invio.invia(iscrizione(), "{}", Duration.ofMinutes(30))).isEqualTo(InvioWebPush.Esito.ERRORE);

        risposta.set(429);
        assertThat(invio.invia(iscrizione(), "{}", Duration.ofMinutes(30))).isEqualTo(InvioWebPush.Esito.ERRORE);
    }

    @Test
    void unServizioSpentoNonFaCadereNiente() {
        Iscrizione iscrizione = iscrizione();
        servizio.stop(0);

        assertThat(invio.invia(iscrizione, "{}", Duration.ofMinutes(30))).isEqualTo(InvioWebPush.Esito.ERRORE);
    }
}
