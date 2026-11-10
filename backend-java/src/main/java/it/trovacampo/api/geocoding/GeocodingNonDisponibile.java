package it.trovacampo.api.geocoding;

import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Nominatim non ha risposto, o ha detto di riprovare: rete, timeout, 5xx,
 * 429 o 408. Non vuol dire che l'indirizzo non esista, e chi riceve questa
 * eccezione non deve scriverlo da nessuna parte come «non riconosciuto»: la
 * ricerca va ripetuta quando il servizio torna.
 *
 * <p>Una risposta valida senza risultati, o un altro errore del client (un 400
 * per un indirizzo che il servizio non accetta), resta invece un
 * {@code Optional.empty()}: riprovare darebbe lo stesso esito.
 */
public class GeocodingNonDisponibile extends RuntimeException {

    public GeocodingNonDisponibile(String messaggio, Throwable causa) {
        super(messaggio, causa);
    }

    /** Vero se l'errore e' del servizio o della rete e non della richiesta. */
    public static boolean transitorio(RestClientException eccezione) {
        if (eccezione instanceof ResourceAccessException) {
            return true;
        }
        if (eccezione instanceof RestClientResponseException risposta) {
            int stato = risposta.getStatusCode().value();
            return stato >= 500 || stato == 429 || stato == 408;
        }
        return false;
    }
}
