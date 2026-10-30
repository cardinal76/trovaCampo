package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import it.trovacampo.api.service.RitmoNominatim;
import java.time.Duration;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Risposte vere di Nominatim (jsonv2, addressdetails), ridotte ai campi che contano. */
class RicercaCampiOsmTest {

    private final RestClient.Builder builder = RestClient.builder().baseUrl("https://nominatim.example");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final RicercaCampiOsm osm =
            new RicercaCampiOsm(builder.build(), new RitmoNominatim(Duration.ZERO));

    private static final String ROMULEA =
            """
            [{"lat":"41.8836901","lon":"12.5049563","category":"leisure","type":"sports_centre",
              "name":"SS Romulea","address":{"road":"Via Farsalo","house_number":"21","city":"Roma"}}]
            """;

    private static final String RONCIGLIONE =
            """
            [{"lat":"42.30","lon":"12.20","category":"highway","type":"residential","name":"Via del Campo Sportivo",
              "address":{"road":"Via del Campo Sportivo","town":"Ronciglione"}},
             {"lat":"42.28","lon":"12.21","category":"leisure","type":"pitch","name":"Campo",
              "address":{"road":"Via Roma","village":"Caprarola"}},
             {"lat":"42.2829275","lon":"12.2175476","category":"leisure","type":"sports_centre",
              "name":"Campo Sportivo \\"Marco Bigi\\"","address":{"road":"Via San Giovanni","town":"Ronciglione"}}]
            """;

    @Test
    void ilCampoColNomeDellaSocieta() {
        server.expect(requestTo(Matchers.startsWith("https://nominatim.example/search")))
                .andExpect(queryParam("q", "romulea%20Roma"))
                .andExpect(queryParam("format", "jsonv2"))
                .andExpect(queryParam("bounded", "1"))
                .andRespond(withSuccess(ROMULEA, MediaType.APPLICATION_JSON));

        assertThat(osm.cerca(new SocietaDaLocalizzare("S.S. ROMULEA A.S.D.", "Roma")))
                .hasValueSatisfying(indirizzo -> {
                    assertThat(indirizzo.indirizzo()).isEqualTo("Via Farsalo 21");
                    assertThat(indirizzo.lat()).isEqualTo(41.8836901);
                    assertThat(indirizzo.fonte()).isEqualTo(Fonte.GEOCODING_OSM);
                    assertThat(indirizzo.confidenza()).isEqualTo(Confidenza.BASSA);
                    assertThat(indirizzo.attribuzione()).contains("OpenStreetMap", "ODbL");
                });
        server.verify();
    }

    @Test
    void altrimentiIlCampoSportivoDelPaese_soloLeisureESoloNelComune() {
        server.expect(queryParam("q", "vicus%20Ronciglione"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        server.expect(queryParam("q", "campo%20sportivo%20Ronciglione"))
                .andRespond(withSuccess(RONCIGLIONE, MediaType.APPLICATION_JSON));

        assertThat(osm.cerca(new SocietaDaLocalizzare("A.S.D. VICUS", "Ronciglione")))
                .hasValueSatisfying(indirizzo -> {
                    assertThat(indirizzo.indirizzo()).isEqualTo("Via San Giovanni");
                    assertThat(indirizzo.comune()).isEqualTo("Ronciglione");
                    assertThat(indirizzo.punteggio()).isEqualTo(RicercaCampiOsm.PUNTEGGIO_CAMPO_DEL_PAESE);
                });
        server.verify();
    }

    @Test
    void nominatimCheNonRispondeNonEUnErrore() {
        server.expect(queryParam("q", "vicus%20Ronciglione")).andRespond(withServerError());
        server.expect(queryParam("q", "campo%20sportivo%20Ronciglione")).andRespond(withServerError());

        assertThat(osm.cerca(new SocietaDaLocalizzare("A.S.D. VICUS", "Ronciglione"))).isEmpty();
    }

    @Test
    void senzaComuneNessunaRicerca() {
        assertThat(osm.cerca(new SocietaDaLocalizzare("A.S.D. VICUS", null))).isEmpty();
        server.verify();
    }
}
