package it.trovacampo.api.geocoding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withTooManyRequests;

import java.io.IOException;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * La differenza che conta: un indirizzo non riconosciuto e' {@code Optional.empty()},
 * un servizio che non risponde e' {@link GeocodingNonDisponibile}. Chi geocodifica in
 * automatico segna come «non riconosciuto» solo il primo.
 */
class NominatimGeocodingTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://nominatim.example");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final NominatimGeocoding geocoding =
            new NominatimGeocoding(
                    builder.build(),
                    new RitmoNominatim(
                            new LimitatoreRichieste(Duration.ZERO, System::nanoTime, millisecondi -> {})));

    private void rispondi(org.springframework.test.web.client.ResponseCreator risposta) {
        server.expect(requestTo(Matchers.startsWith("https://nominatim.example/search"))).andRespond(risposta);
    }

    @Test
    void trovaLeCoordinate() {
        rispondi(withSuccess("[{\"lat\":\"41.9\",\"lon\":\"12.5\"}]", MediaType.APPLICATION_JSON));

        assertThat(geocoding.geocodifica("Via della Certosa 12"))
                .contains(new Geocoding.Coordinate(41.9, 12.5));
    }

    @Test
    void unaRispostaSenzaRisultatiENonRiconosciuto() {
        rispondi(withSuccess("[]", MediaType.APPLICATION_JSON));

        assertThat(geocoding.geocodifica("Via inesistente 1")).isEmpty();
    }

    @Test
    void unaRichiestaRifiutataNonSiRiprova() {
        rispondi(withBadRequest());

        assertThat(geocoding.geocodifica("???")).isEmpty();
    }

    @Test
    void unaRispostaIllegibileENonRiconosciuto() {
        rispondi(withSuccess("<html>non json</html>", MediaType.APPLICATION_JSON));

        assertThat(geocoding.geocodifica("Via della Certosa 12")).isEmpty();
    }

    @Test
    void unErroreDelServerENonDisponibile() {
        rispondi(withServerError());

        assertThatThrownBy(() -> geocoding.geocodifica("Via della Certosa 12"))
                .isInstanceOf(GeocodingNonDisponibile.class);
    }

    @Test
    void troppeRichiesteENonDisponibile() {
        rispondi(withTooManyRequests());

        assertThatThrownBy(() -> geocoding.geocodifica("Via della Certosa 12"))
                .isInstanceOf(GeocodingNonDisponibile.class);
    }

    @Test
    void unTimeoutDiRichiestaENonDisponibile() {
        rispondi(withStatus(HttpStatus.REQUEST_TIMEOUT));

        assertThatThrownBy(() -> geocoding.geocodifica("Via della Certosa 12"))
                .isInstanceOf(GeocodingNonDisponibile.class);
    }

    @Test
    void laReteCheCadeENonDisponibile() {
        rispondi(withException(new IOException("connessione rifiutata")));

        assertThatThrownBy(() -> geocoding.geocodifica("Via della Certosa 12"))
                .isInstanceOf(GeocodingNonDisponibile.class)
                .hasCauseInstanceOf(org.springframework.web.client.ResourceAccessException.class);
    }
}
