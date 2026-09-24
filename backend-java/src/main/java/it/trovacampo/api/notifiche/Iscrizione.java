package it.trovacampo.api.notifiche;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un browser (o un telefono) iscritto alle notifiche, senza login: tutto
 * quello che TrovaCampo sa di chi lo usa sta qui, ed è solo quello che serve
 * a mandare gli avvisi che ha chiesto.
 *
 * <p>L'id è l'impronta SHA-256 dell'endpoint push: l'endpoint lo conosce solo
 * quel browser (è un indirizzo lungo e casuale del servizio push), quindi
 * vale come credenziale per aggiornare o cancellare l'iscrizione, e l'id non
 * dipende dalla sua lunghezza.
 *
 * @param endpoint dove mandare le notifiche: https, di un servizio push noto
 * @param p256dh la chiave pubblica del browser, per cifrare
 * @param auth il segreto condiviso con il browser, per cifrare
 * @param squadre le chiavi delle squadre seguite (vedi {@link ChiaveSquadra})
 * @param lat la posizione salvata, arrotondata a un centinaio di metri
 * @param raggioKm per l'avviso delle partite vicine
 * @param posizioneAggiornataIl quando il telefono ha mandato l'ultima posizione
 * @param aggiornataIl l'ultima volta che il sito è stato aperto con le notifiche accese
 */
@Document(collection = "iscrizioni_notifiche")
public record Iscrizione(
        @Id String id,
        String endpoint,
        String p256dh,
        String auth,
        List<String> squadre,
        boolean avvisoSquadre,
        boolean avvisoVicino,
        Double lat,
        Double lng,
        Integer raggioKm,
        Instant posizioneAggiornataIl,
        Instant aggiornataIl) {

    public static String idDi(String endpoint) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(endpoint.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException eccezione) {
            throw new IllegalStateException(eccezione);
        }
    }

    public boolean haPosizione() {
        return lat != null && lng != null && raggioKm != null;
    }

    /** L'endpoint è una credenziale: nei log e nelle eccezioni non si stampa. */
    @Override
    public String toString() {
        return "Iscrizione[" + id.substring(0, 12) + "…]";
    }
}
