package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** Righe vere del censimento CC0 della Città metropolitana, BOM e virgolette sbagliate comprese. */
class ImportatoreImpiantiOpenDataTest {

    private static final String URL = "https://opendata.example/impiantisportivi.csv";

    private static final String CSV =
            "﻿\"NomeImpianto\",\"Comune\",\"Municipio\",\"Indirizzo\",\"Struttura\",\"Attivita\",\"Sport\",\"Descrizione\"\n"
                    + "\"I.I.S. Benedetto Croce\",\"ROMA\",\"MUNICIPIO-05\",\"Via B. Bardanzellu, 7\","
                    + "\"Palestra 20,3x29,5m (linoleum, 150 posti), 2 campi calcio a 5 (erba sintetica)\","
                    + "\"Attività sportiva ed agonistica\",\"Basket\",\"AGONISTICA\"\n"
                    + "\"I.I.S. Benedetto Croce\",\"ROMA\",\"MUNICIPIO-05\",\"Via B. Bardanzellu, 7\","
                    + "\"Palestra 20,3x29,5m (linoleum, 150 posti), 2 campi calcio a 5 (erba sintetica)\","
                    + "\"Attività sportiva ed agonistica\",\"Calcio-5\",\"AGONISTICA\"\n"
                    + "\"Metropolis Sporting Club\",\"ROMA\",\"MUNICIPIO-07\",\"Via A. Vertunni, 131\",\"Palestra\","
                    + "\"Attività sportiva\",\"Judo\",\"SPORTIVA\"\n"
                    + "\"Colle Oppio \"\"La Polveriera\"\"\",\"ROMA\",\"MUNICIPIO-01\",\"Via del Monte Oppio\","
                    + "\"Campo calcio a 7 (terra), Campo calcio a 5\",\"Ristrutturazione\",\"Calcio-11\",\"RISTRUTTURAZIONE\"\n"
                    + "\"Stadio Comunale\",\"Civitavecchia\",\"\",\"Viale della Vittoria\",\"Campo calcio\","
                    + "\"Attività agonistica\",\"Calcio-11\",\"AGONISTICA\"\r\n";

    @Test
    void importaSoloGliImpiantiConIlCalcioUnoPerImpianto() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andRespond(withSuccess(CSV.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN));
        ImpiantiOpenDataRepository repository = mock(ImpiantiOpenDataRepository.class);
        Instant adesso = Instant.parse("2026-10-30T12:00:00Z");
        ImportatoreImpiantiOpenData importatore =
                new ImportatoreImpiantiOpenData(
                        repository, builder.build(), URL, Clock.fixed(adesso, ZoneOffset.UTC));

        ImportatoreImpiantiOpenData.Esito esito = importatore.importa();

        assertThat(esito.righe()).isEqualTo(5);
        assertThat(esito.impianti()).isEqualTo(3);
        assertThat(esito.conCalcioA11()).isEqualTo(2);
        assertThat(esito.licenza()).isEqualTo("CC0 1.0");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImpiantoOpenData>> salvati = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(salvati.capture());
        assertThat(salvati.getValue())
                .extracting(ImpiantoOpenData::nome)
                .containsExactly("I.I.S. Benedetto Croce", "Colle Oppio \"La Polveriera\"", "Stadio Comunale");
        ImpiantoOpenData croce = salvati.getValue().get(0);
        assertThat(croce.sport()).containsExactly("Calcio-5");
        assertThat(croce.indirizzo()).isEqualTo("Via B. Bardanzellu, 7");
        assertThat(croce.municipio()).isEqualTo("MUNICIPIO-05");
        assertThat(croce.lat()).isNull();
        assertThat(croce.importatoIl()).isEqualTo(adesso);
        // Lo stesso impianto reimportato ha lo stesso id: si sovrascrive.
        assertThat(croce.id()).isEqualTo(
                ImportatoreImpiantiOpenData.impianti(LettoreCsv.leggi(CSV, ','), adesso).get(0).id());
        server.verify();
    }

    @Test
    void laDisciplinaFinitaNellaStrutturaContaLoStesso() {
        List<List<String>> righe =
                List.of(
                        List.of("NomeImpianto", "Comune", "Indirizzo", "Struttura", "Sport"),
                        List.of("Polisportiva X", "ROMA", "Via Y 1", "2 campi calcio a 5 (sintetico)", "25m scoperta)"));

        assertThat(ImportatoreImpiantiOpenData.impianti(righe, Instant.EPOCH))
                .singleElement()
                .satisfies(impianto -> assertThat(impianto.sport()).containsExactly("Calcio"));
    }

    @Test
    void ilCsvConPuntoEVirgolaEAcapoNeiCampi() {
        assertThat(LettoreCsv.leggi("a;\"b;\nc\";d\r\n\r\ne;f;g", ';'))
                .containsExactly(List.of("a", "b;\nc", "d"), List.of("e", "f", "g"));
    }
}
