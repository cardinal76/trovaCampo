package it.trovacampo.api.geocoding;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Geocodifica con Nominatim (OpenStreetMap): gratuito e senza chiave API, ma
 * soggetto alla sua usage policy
 * (https://operations.osmfoundation.org/policies/nominatim/): al massimo una
 * richiesta al secondo, niente uso massivo e User-Agent obbligatorio che
 * identifichi l'applicazione. Per volumi più alti serve un provider a
 * pagamento o un'istanza Nominatim self-hosted.
 */
@EnableConfigurationProperties(ProprietaNominatim.class)
@Component
public class NominatimGeocoding implements Geocoding {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocoding.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;
    private final RitmoNominatim ritmo;

    @Autowired
    public NominatimGeocoding(
            RestClient.Builder builder, ProprietaNominatim proprieta, RitmoNominatim ritmo) {
        this(builder, proprieta.url(), proprieta.userAgent(), ritmo);
    }

    NominatimGeocoding(RestClient.Builder builder, String url, String userAgent, RitmoNominatim ritmo) {
        this(client(builder, url, userAgent), ritmo);
    }

    /** Per i test, con un client gia' pronto. */
    NominatimGeocoding(RestClient client, RitmoNominatim ritmo) {
        this.client = client;
        this.ritmo = ritmo;
    }

    private static RestClient client(RestClient.Builder builder, String url, String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);

        return builder.baseUrl(url)
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    @Override
    public Optional<Coordinate> geocodifica(String indirizzo) {
        try {
            ritmo.attendi();
            RisultatoNominatim[] risultati =
                    client.get()
                            .uri(
                                    uri ->
                                            uri.path("/search")
                                                    .queryParam("format", "json")
                                                    .queryParam("limit", 1)
                                                    .queryParam("q", indirizzo)
                                                    .build())
                            .retrieve()
                            .body(RisultatoNominatim[].class);

            if (risultati == null || risultati.length == 0) {
                return Optional.empty();
            }

            return Optional.of(
                    new Coordinate(
                            Double.parseDouble(risultati[0].lat()),
                            Double.parseDouble(risultati[0].lon())));
        } catch (RestClientException eccezione) {
            if (GeocodingNonDisponibile.transitorio(eccezione)) {
                // Non e' l'indirizzo: chi chiama non deve darlo per perso.
                throw new GeocodingNonDisponibile("Nominatim non risponde", eccezione);
            }
            return nonRiconosciuto(indirizzo, eccezione);
        } catch (RuntimeException eccezione) {
            return nonRiconosciuto(indirizzo, eccezione);
        }
    }

    /** Un indirizzo non geocodificato non deve impedire il salvataggio del campo: comparira' in ricerca senza posizione sulla mappa. */
    private Optional<Coordinate> nonRiconosciuto(String indirizzo, RuntimeException eccezione) {
        log.warn("Geocodifica non riuscita per '{}': {}", indirizzo, eccezione.toString());
        return Optional.empty();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RisultatoNominatim(String lat, String lon) {}
}
