package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Le risposte vere di presenze, campi in più compresi, lette nei record di TrovaCampo. */
class AnagraficaPresenzeTest {

    private final RestClient.Builder builder =
            RestClient.builder().baseUrl("http://presenze-backend:8080");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private final AnagraficaPresenze anagrafica = new AnagraficaPresenze(builder.build());

    @Test
    void leggeICampiConLeSocietaCheCiGiocano() {
        server.expect(requestTo("http://presenze-backend:8080/api/pubblico/anagrafica/impianti"))
                .andRespond(
                        withSuccess(
                                """
                                [{"id":190,"nome":"DON ORIONE","fondo":"SINTEX",
                                  "indirizzo":"VIA DELLA CAMILLUCCIA 120","comune":"ROMA",
                                  "lat":41.93,"lng":12.44,
                                  "societa":[{"id":7,"denominazione":"BOREALE"}]},
                                 {"id":191,"nome":"COMUNALE","fondo":null,"indirizzo":null,
                                  "comune":"ARCE","lat":null,"lng":null,"societa":[]}]
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(anagrafica.impianti())
                .first()
                .satisfies(
                        campo -> {
                            assertThat(campo.nome()).isEqualTo("DON ORIONE");
                            assertThat(campo.lat()).isEqualTo(41.93);
                            assertThat(campo.societa())
                                    .containsExactly(
                                            new AnagraficaPresenze.Riferimento(7L, "BOREALE"));
                        });
        server.verify();
    }

    @Test
    void leggeLaSchedaConLeSquadre() {
        server.expect(requestTo("http://presenze-backend:8080/api/pubblico/anagrafica/societa/7"))
                .andRespond(
                        withSuccess(
                                """
                                {"id":7,"denominazione":"BOREALE",
                                 "campi":[{"id":190,"nome":"DON ORIONE","fondo":"SINTEX",
                                   "indirizzo":"VIA DELLA CAMILLUCCIA 120","comune":"ROMA",
                                   "lat":null,"lng":null}],
                                 "squadre":[{"campionatoId":3,"campionato":"ECCELLENZA",
                                   "ente":"Regionali","stagione":"2026/2027","gironeId":11,
                                   "girone":"A","squadra":"","fuoriClassifica":false,"campoId":190}]}
                                """,
                                MediaType.APPLICATION_JSON));

        AnagraficaPresenze.Scheda scheda = anagrafica.scheda(7);

        assertThat(scheda.denominazione()).isEqualTo("BOREALE");
        assertThat(scheda.squadre())
                .containsExactly(
                        new AnagraficaPresenze.Squadra(
                                "ECCELLENZA", "Regionali", "2026/2027", "A", "", false, 190L));
    }
}
