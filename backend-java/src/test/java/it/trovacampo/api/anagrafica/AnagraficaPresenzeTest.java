package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
                                  "societa":[{"id":7,"denominazione":"BOREALE"},
                                    {"id":8,"denominazione":"LODIGIANI",
                                     "logoUrl":"https://play.lnd.it/lndimg/8/8-web.png"}]},
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
                                            new AnagraficaPresenze.Riferimento(7L, "BOREALE"),
                                            // Lo stemma, quando presenze ce l'ha.
                                            new AnagraficaPresenze.Riferimento(
                                                    8L, "LODIGIANI",
                                                    "https://play.lnd.it/lndimg/8/8-web.png"));
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

    /**
     * Mentre il portale LND non risponde, presenze dice a quale comunicato
     * sono aggiornati i risultati di ogni girone.
     */
    @Test
    void leggeAQualeComunicatoSonoAggiornatiIRisultati() {
        server.expect(requestTo("http://presenze-backend:8080/api/pubblico/anagrafica/societa/7"))
                .andRespond(
                        withSuccess(
                                """
                                {"id":7,"denominazione":"BOREALE","campi":[],
                                 "squadre":[{"campionatoId":3,"campionato":"ECCELLENZA",
                                   "ente":"Regionali","stagione":"2026/2027","gironeId":11,
                                   "girone":"A","squadra":"","fuoriClassifica":false,"campoId":190,
                                   "risultatiDaComunicato":{"numero":55,"data":"2026-09-24",
                                     "ente":"Regionali","portaleNonDisponibileDal":"2026-09-26T11:00:00Z"}}]}
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(anagrafica.scheda(7).squadre().getFirst().risultatiDaComunicato())
                .isEqualTo(
                        new AnagraficaPresenze.RisultatiDaComunicato(
                                55,
                                java.time.LocalDate.of(2026, 9, 24),
                                "Regionali",
                                java.time.Instant.parse("2026-09-26T11:00:00Z")));
    }

    @Test
    void cercaUnaSocietaPerNome() {
        server.expect(
                        requestTo(
                                "http://presenze-backend:8080/api/pubblico/anagrafica/societa?q=A.S.D.%20Boreale"))
                .andRespond(
                        withSuccess(
                                """
                                [{"id":7,"denominazione":"BOREALE",
                                  "campo":{"id":190,"nome":"DON ORIONE"}}]
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(anagrafica.cerca("A.S.D. Boreale"))
                .containsExactly(new AnagraficaPresenze.Riferimento(7L, "BOREALE"));
    }

    @Test
    void leggeLePartiteDiUnIntervalloConIlCampo() {
        server.expect(
                        requestTo(
                                "http://presenze-backend:8080/api/pubblico/anagrafica/partite?dal=2026-09-05&al=2026-09-18"))
                .andRespond(
                        withSuccess(
                                """
                                [{"id":9,"dataOra":"2026-09-06T11:00:00+02:00","stato":"DA_GIOCARE",
                                  "impiantoId":190,"casa":"BOREALE","ospite":"VIGOR PERCONTI",
                                  "casaSocietaId":7,"ospiteSocietaId":8,"campionatoId":3,
                                  "campionato":"ECCELLENZA","ente":"Regionali","gironeId":11,
                                  "girone":"A","giornata":1,"ritorno":false}]
                                """,
                                MediaType.APPLICATION_JSON));

        assertThat(anagrafica.partite(LocalDate.of(2026, 9, 5), LocalDate.of(2026, 9, 18)))
                .singleElement()
                .satisfies(
                        partita -> {
                            assertThat(partita.impiantoId()).isEqualTo(190L);
                            assertThat(partita.dataOra().toInstant())
                                    .isEqualTo(
                                            OffsetDateTime.of(2026, 9, 6, 9, 0, 0, 0, ZoneOffset.UTC)
                                                    .toInstant());
                            assertThat(partita.casa()).isEqualTo("BOREALE");
                            assertThat(partita.girone()).isEqualTo("A");
                        });
        server.verify();
    }

    /**
     * Il contratto degli ultimi risultati, con il JSON cosi\u0027 come lo manda
     * presenze: se un campo cambia nome, questo test cade qui e non in una
     * scheda vuota in produzione.
     */
    @Test
    void leggeGliUltimiRisultatiDiUnaSocieta() {
        server.expect(
                        requestTo(
                                "http://presenze-backend:8080/api/pubblico/anagrafica/societa/123/risultati"))
                .andRespond(
                        withSuccess(
                                """
                                {"societa":{"id":123,"denominazione":"A.S.D. ESEMPIO"},
                                 "risultati":[
                                   {"partitaId":9912,"squadraIscrittaId":50,
                                    "data":"2026-10-11T10:30:00+02:00",
                                    "campionatoId":44,"campionato":"UNDER 17 PROVINCIALE",
                                    "ente":"Roma","stagione":"2026-2027","gironeId":77,"girone":"A",
                                    "giornata":3,"ritorno":false,"squadra":"",
                                    "casa":"A.S.D. ESEMPIO","ospite":"ALTRA A.S.D.",
                                    "golCasa":2,"golOspite":1,"inCasa":true,"esito":"VITTORIA"},
                                   {"partitaId":9900,"squadraIscrittaId":51,"data":null,
                                    "campionatoId":44,"campionato":"UNDER 17 PROVINCIALE",
                                    "ente":null,"stagione":"2026-2027","gironeId":null,"girone":null,
                                    "giornata":2,"ritorno":false,"squadra":"B",
                                    "casa":"TERZA A.S.D.","ospite":"A.S.D. ESEMPIO",
                                    "golCasa":3,"golOspite":0,"inCasa":false,"esito":"SCONFITTA"}]}
                                """,
                                MediaType.APPLICATION_JSON));

        AnagraficaPresenze.Risultati risultati = anagrafica.risultati(123);

        assertThat(risultati.societa())
                .isEqualTo(new AnagraficaPresenze.Riferimento(123L, "A.S.D. ESEMPIO"));
        assertThat(risultati.risultati())
                .containsExactly(
                        // Jackson riporta l'istante a UTC: RisultatiSocieta lo rimette
                        // sul fuso di Roma prima di darlo all'app, come per le partite.
                        new AnagraficaPresenze.Risultato(
                                9912L, 50L,
                                OffsetDateTime.of(2026, 10, 11, 8, 30, 0, 0, ZoneOffset.UTC),
                                44L, "UNDER 17 PROVINCIALE", "Roma", "2026-2027", 77L, "A", 3, false,
                                "", "A.S.D. ESEMPIO", "ALTRA A.S.D.", 2, 1, true, "VITTORIA"),
                        // Data, ente e girone possono mancare: nulli, non vuoti.
                        new AnagraficaPresenze.Risultato(
                                9900L, 51L, null, 44L, "UNDER 17 PROVINCIALE", null, "2026-2027",
                                null, null, 2, false, "B", "TERZA A.S.D.", "A.S.D. ESEMPIO", 3, 0,
                                false, "SCONFITTA"));
        server.verify();
    }
}
