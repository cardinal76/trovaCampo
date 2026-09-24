package it.trovacampo.api.notifiche;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manda una notifica al servizio push del browser (Google, Mozilla, Apple,
 * Microsoft), che la consegna al telefono anche a sito chiuso.
 */
@Component
public class InvioWebPush {

    private static final Logger log = LoggerFactory.getLogger(InvioWebPush.class);

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** Il JWT VAPID può valere al massimo 24 ore; ne basta molto meno. */
    private static final Duration VALIDITA_FIRMA = Duration.ofHours(12);

    public enum Esito {
        /** Il servizio push l'ha presa in carico. */
        CONSEGNATA,
        /** L'iscrizione non esiste più (404 o 410): va cancellata. */
        SCADUTA,
        /** Rete, 5xx, 429, o un rifiuto che non dice nulla sull'iscrizione: si riprova al giro dopo. */
        ERRORE
    }

    private final HttpClient client;
    private final DepositoChiaviVapid chiavi;
    private final String contatto;
    private final Clock orologio;

    @Autowired
    public InvioWebPush(
            DepositoChiaviVapid chiavi,
            @Value("${trovacampo.notifiche.contatto:https://trovacampo.footballer.it}") String contatto) {
        this(
                HttpClient.newBuilder().connectTimeout(TIMEOUT).followRedirects(HttpClient.Redirect.NEVER).build(),
                chiavi,
                contatto,
                Clock.systemUTC());
    }

    InvioWebPush(HttpClient client, DepositoChiaviVapid chiavi, String contatto, Clock orologio) {
        this.client = client;
        this.chiavi = chiavi;
        this.contatto = contatto;
        this.orologio = orologio;
    }

    /**
     * @param contenuto il JSON che il service worker legge (vedi public/sw.js del frontend)
     * @param durata per quanto il servizio push la tiene se il telefono è
     *     spento: oltre, un avviso "tra un'ora" non serve più
     */
    public Esito invia(Iscrizione iscrizione, String contenuto, Duration durata) {
        URI endpoint = URI.create(iscrizione.endpoint());
        String destinatario = endpoint.getScheme() + "://" + endpoint.getRawAuthority();
        try {
            byte[] corpo =
                    CrittografiaWebPush.cifra(
                            contenuto.getBytes(StandardCharsets.UTF_8), iscrizione.p256dh(), iscrizione.auth());
            HttpRequest richiesta =
                    HttpRequest.newBuilder(endpoint)
                            .timeout(TIMEOUT)
                            .header("Content-Type", "application/octet-stream")
                            .header("Content-Encoding", "aes128gcm")
                            .header("TTL", String.valueOf(durata.toSeconds()))
                            // "high" sveglia il telefono anche in risparmio energetico:
                            // un avviso che arriva dopo la partita non serve.
                            .header("Urgency", "high")
                            .header(
                                    "Authorization",
                                    CrittografiaWebPush.autorizzazione(
                                            chiavi.chiavi(), destinatario, contatto,
                                            orologio.instant().plus(VALIDITA_FIRMA)))
                            .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                            .build();
            HttpResponse<String> risposta = client.send(richiesta, HttpResponse.BodyHandlers.ofString());
            return esito(risposta.statusCode(), endpoint.getHost(), risposta.body());
        } catch (IllegalArgumentException chiaviRotte) {
            // Chiavi del browser che non stanno sulla curva: l'iscrizione non
            // potrà mai ricevere niente, tanto vale toglierla.
            log.warn("Iscrizione {} con chiavi non valide: la tolgo", iscrizione);
            return Esito.SCADUTA;
        } catch (IOException eccezione) {
            log.warn("Servizio push {} non raggiungibile: {}", endpoint.getHost(), eccezione.toString());
            return Esito.ERRORE;
        } catch (InterruptedException eccezione) {
            Thread.currentThread().interrupt();
            return Esito.ERRORE;
        }
    }

    /** Solo l'host nei log: l'endpoint intero basterebbe a mandare notifiche a quel telefono. */
    private static Esito esito(int stato, String host, String corpo) {
        if (stato >= 200 && stato < 300) {
            return Esito.CONSEGNATA;
        }
        if (stato == 404 || stato == 410) {
            return Esito.SCADUTA;
        }
        String motivo = corpo == null ? "" : corpo.substring(0, Math.min(corpo.length(), 200));
        log.warn("Il servizio push {} ha risposto {}: {}", host, stato, motivo);
        return Esito.ERRORE;
    }
}
