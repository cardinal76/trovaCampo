package it.trovacampo.api.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Geocodifica con Nominatim (OpenStreetMap): gratuito e senza chiave API, ma
 * soggetto alla sua usage policy
 * (https://operations.osmfoundation.org/policies/nominatim/): al massimo una
 * richiesta al secondo, niente uso massivo e User-Agent obbligatorio che
 * identifichi l'applicazione. Per volumi più alti serve un provider a
 * pagamento o un'istanza Nominatim self-hosted.
 */
@Component
public class NominatimGeocoding implements Geocoding {

    private static final Logger log = LoggerFactory.getLogger(NominatimGeocoding.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final RestClient client;

    public NominatimGeocoding(
            RestClient.Builder builder,
            @Value("${trovacampo.nominatim.url}") String url,
            @Value("${trovacampo.nominatim.user-agent}") String userAgent) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(TIMEOUT);
        requestFactory.setReadTimeout(TIMEOUT);

        this.client =
                builder.baseUrl(url)
                        .requestFactory(requestFactory)
                        .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                        .build();
    }

    @Override
    public Optional<Coordinate> geocodifica(String indirizzo) {
        try {
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
        } catch (RuntimeException eccezione) {
            // Un indirizzo non geocodificato non deve impedire il salvataggio
            // del campo: comparirà in ricerca senza posizione sulla mappa.
            log.warn("Geocodifica non riuscita per '{}': {}", indirizzo, eccezione.toString());
            return Optional.empty();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RisultatoNominatim(String lat, String lon) {}
}
