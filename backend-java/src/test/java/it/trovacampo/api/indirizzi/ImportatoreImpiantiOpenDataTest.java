package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Righe vere del censimento CC0 della Città metropolitana, BOM e virgolette
 * sbagliate comprese; per il Veneto righe inventate con i due modelli di
 * dati.veneto.it (Alto Vicentino e Verona).
 */
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
                        repository, builder.build(), URL, new OpenDataVeneto(null, List.of()), millis -> {},
                        Clock.fixed(adesso, ZoneOffset.UTC));

        ImportatoreImpiantiOpenData.Esito esito = importatore.importa();

        assertThat(esito.righe()).isEqualTo(5);
        assertThat(esito.impianti()).isEqualTo(3);
        assertThat(esito.conCalcioA11()).isEqualTo(2);
        assertThat(esito.licenza()).isEqualTo("CC0 1.0");
        assertThat(esito.conCoordinate()).isZero();
        assertThat(esito.nonLetti()).isEmpty();

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
        assertThat(croce.attribuzioneDaMostrare()).contains("Città metropolitana di Roma Capitale", "CC0 1.0");
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

    // ---- Veneto: i due modelli di dati.veneto.it, con righe inventate ----

    private static final MediaType CSV_TIPO = MediaType.parseMediaType("text/csv");

    /** Il modello dei comuni vicentini, in Windows-1252, con contatti ed email che non vanno salvati. */
    private static final String VICENTINO =
            "\"ID\";\"COD_ISTAT_Comune\";\"Impianto_Tipologia\";\"Impianto_Denominazione\";\"Impianto_Via\";"
                    + "\"Impianto_Civico\";\"Impianto_Comune\";\"Impianto_Provincia\";\"Impianto_Disciplina\";"
                    + "\"Impianto_Descrizione\";\"Impianto_SitoWeb\";\"Impianto_TipoAccesso\";\"Impianto_Contatto\";"
                    + "\"Impianto_Contatto_Email\";\"Longitudine\";\"Latitudine\";\r\n"
                    + "\"P1\";\"024999\";\"campo calcio\";\"Impianto Calcio di Prova\";\"Via della Libertà\";\"1\";"
                    + "\"Paesello\";\"Vicenza\";\"calcio\";\"campo in erba\nnaturale\";\"\";\"Previo contatto\";"
                    + "\"Mario Rossi\";\"mario.rossi@example.it\";\"11.37930\";\"45.71744\";\r\n"
                    + "\"P2\";\"024999\";\"Palestra scolastica\";\"Palestra Bassi\";\"Via Scuole 3\";\"3\";\"Paesello\";"
                    + "\"Vicenza\";\"Basket, calcio a 5, pallavolo\";\"\";\"\";\"\";\"Ufficio Sport\";\"\";;\r\n"
                    + "\"P3\";\"024999\";\"Campo da calcio\";\"Campo da calcetto Est\";\"Via Est\";\"\";\"Paesello\";"
                    + "\"Vicenza\";\"Calcio\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\r\n"
                    + "\"P4\";\"024999\";\"Piscina\";\"Piscina comunale\";\"Via Acqua\";\"2\";\"Paesello\";\"Vicenza\";"
                    + "\"nuoto\";\"\";\"\";\"\";\"Luigi Bianchi\";\"luigi@example.it\";\"11.4\";\"45.7\";\r\n"
                    + "\"P5\";\"024999\";\"Campo da Calcio\";\"Campo Robur ? Nord\";\"Via Nord\";\"\";\"Paesello\";"
                    + "\"Vicenza\";\"\";\"\";\"\";\"\";\"\";\"\";\"\";\"\"\r\n";

    /** Il modello di Verona: in UTF-8 con BOM, senza il comune, una riga con lat e lon scambiate. */
    private static final String VERONA =
            "﻿\"ID OGGETTO\";\"Street view\";\"DENOMINAZIONE EDIFICIO\";\"INDIRIZZO\";\"CAP\";"
                    + "\"TIPOLOGIA ATTIVITA' SPORTIVA\";\"OMOGENEA\";\"QUARTIERE\";\"CIRCOSCRIZIONE\";\"LATITUDINE\";"
                    + "\"LONGITUDINE\";\r\n"
                    + "\"1\";\"https://maps.example\";\"Centro sportivo \"\"Alfa\"\"\";\"Via Alfa, 1\";\"37100\";"
                    + "\"Calcio a 11, rugby\";\"X\";\"Q\";\"3 OVEST\";\"45.435\";\"10.966\";\r\n"
                    + "\"2\";\"\";\"Polisportiva Beta\";\"Via Beta, 2\";\"37100\";\"Calcio a 5, tennis\";\"X\";\"Q\";"
                    + "\"8 NORD-EST\";\"11,0678\";\"45,4572\";\r\n"
                    + "\"3\";\"\";\"Piscina Gamma\";\"Via Gamma, 3\";\"37100\";\"Nuoto\";\"X\";\"Q\";\"5 SUD\";"
                    + "\"45.42\";\"10.99\";\r\n";

    private static final OpenDataVeneto.Dataset PAESELLO =
            new OpenDataVeneto.Dataset("Paesello", "Comune di Paesello - Impianti Sportivi", "https://veneto.example/p.csv");
    private static final OpenDataVeneto.Dataset CITTA =
            new OpenDataVeneto.Dataset("Verona", "Comune di Verona - Impianti sportivi", "https://veneto.example/v.csv");
    private static final OpenDataVeneto.Dataset ROTTO =
            new OpenDataVeneto.Dataset("Altrove", "Comune di Altrove - Impianti sportivi", "https://veneto.example/a.csv");

    private static ImportatoreImpiantiOpenData veneto(
            RestClient.Builder builder, ImpiantiOpenDataRepository repository, List<Long> attese,
            OpenDataVeneto.Dataset... dataset) {
        return new ImportatoreImpiantiOpenData(
                repository, builder.build(), URL, new OpenDataVeneto(null, List.of(dataset)), attese::add,
                Clock.fixed(Instant.parse("2026-10-31T09:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void ilVenetoUnFilePerComuneDieciSecondiFraUnoELAltro() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PAESELLO.url()))
                .andRespond(withSuccess(VICENTINO.getBytes(Charset.forName("windows-1252")), CSV_TIPO));
        server.expect(requestTo(CITTA.url()))
                .andRespond(withSuccess(VERONA.getBytes(StandardCharsets.UTF_8), CSV_TIPO));
        server.expect(requestTo(ROTTO.url())).andRespond(withStatus(HttpStatus.NOT_FOUND));
        ImpiantiOpenDataRepository repository = mock(ImpiantiOpenDataRepository.class);
        List<Long> attese = new ArrayList<>();

        ImportatoreImpiantiOpenData.Esito esito =
                veneto(builder, repository, attese, PAESELLO, CITTA, ROTTO).importa(FonteOpenData.VENETO);

        server.verify();
        // Tre richieste, due attese: il robots.txt del portale chiede Crawl-delay: 10.
        assertThat(attese).hasSize(2).allSatisfy(millis -> assertThat(millis).isBetween(9_000L, 10_000L));
        assertThat(esito.fonte()).isEqualTo("veneto-comuni");
        assertThat(esito.licenza()).isEqualTo("CC BY 4.0");
        assertThat(esito.righe()).isEqualTo(8);
        assertThat(esito.impianti()).isEqualTo(6);
        assertThat(esito.conCalcioA11()).isEqualTo(3);
        assertThat(esito.conCoordinate()).isEqualTo(3);
        assertThat(esito.nonLetti()).singleElement().asString().startsWith("Comune di Altrove");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ImpiantoOpenData>> salvati = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(salvati.capture());
        List<ImpiantoOpenData> impianti = salvati.getValue();
        assertThat(impianti)
                .extracting(ImpiantoOpenData::nome)
                .containsExactly(
                        "Impianto Calcio di Prova", "Palestra Bassi", "Campo da calcetto Est", "Campo Robur - Nord",
                        "Centro sportivo \"Alfa\"", "Polisportiva Beta");
        assertThat(impianti).extracting(ImpiantoOpenData::sport)
                .containsExactly(
                        List.of("Calcio-11"), List.of("Calcio-5"), List.of("Calcio-5"), List.of("Calcio-11"),
                        List.of("Calcio-11"), List.of("Calcio-5"));

        ImpiantoOpenData prova = impianti.get(0);
        assertThat(prova.indirizzo()).isEqualTo("Via della Libertà 1");
        assertThat(prova.comune()).isEqualTo("Paesello");
        assertThat(prova.lat()).isEqualTo(45.71744);
        assertThat(prova.lng()).isEqualTo(11.3793);
        assertThat(prova.fonte()).isEqualTo("veneto-comuni");
        assertThat(prova.attribuzioneDaMostrare())
                .isEqualTo("Comune di Paesello - Impianti Sportivi, dati.veneto.it (CC BY 4.0)");
        assertThat(impianti.get(1).lat()).isNull();

        ImpiantoOpenData beta = impianti.get(5);
        assertThat(beta.comune()).isEqualTo("Verona");
        assertThat(beta.municipio()).isEqualTo("8 NORD-EST");
        assertThat(beta.lat()).isEqualTo(45.4572);
        assertThat(beta.lng()).isEqualTo(11.0678);

        // Nessun dato di persone: i contatti e le email del dataset non si leggono.
        assertThat(impianti.toString()).doesNotContain("Rossi", "example.it", "Ufficio Sport");
    }

    @Test
    void unBloccoFermaTuttoSenzaRiprovareNeSalvare() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(PAESELLO.url())).andRespond(withStatus(HttpStatus.FORBIDDEN));
        ImpiantiOpenDataRepository repository = mock(ImpiantiOpenDataRepository.class);

        assertThatThrownBy(() -> veneto(builder, repository, new ArrayList<>(), PAESELLO, CITTA)
                        .importa(FonteOpenData.VENETO))
                .isInstanceOf(ImportatoreImpiantiOpenData.OpenDataBloccatoException.class)
                .hasMessageContaining("403");
        server.verify();
        verify(repository, never()).saveAll(any());
    }

    @Test
    void unaPaginaHtmlAlPostoDelCsvEUnBlocco() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(URL))
                .andRespond(withSuccess("<html>Accesso non consentito</html>", MediaType.TEXT_HTML));
        ImpiantiOpenDataRepository repository = mock(ImpiantiOpenDataRepository.class);

        assertThatThrownBy(() -> veneto(builder, repository, new ArrayList<>()).importa(FonteOpenData.CMRC))
                .isInstanceOf(ImportatoreImpiantiOpenData.OpenDataBloccatoException.class)
                .hasMessageContaining("HTML");
        verify(repository, never()).saveAll(any());
    }

    @Test
    void ilVenetoSenzaDatasetConfiguratiLoDice() {
        assertThatThrownBy(() -> veneto(RestClient.builder(), mock(ImpiantiOpenDataRepository.class),
                        new ArrayList<>()).importa(FonteOpenData.VENETO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Nessun dataset del Veneto");
    }

    @Test
    void loStessoImpiantoDueVolteUnisceLeDiscipline() {
        List<List<String>> righe =
                List.of(
                        List.of("Impianto_Tipologia", "Impianto_Denominazione", "Impianto_Via", "Impianto_Comune",
                                "Impianto_Disciplina", "Latitudine", "Longitudine"),
                        List.of("Campo Calcio", "Impianto Via Maso - Campo da calcio", "Via Maso", "Paesello",
                                "Calcio", "", ""),
                        List.of("Campo Calcio", "Impianto Via Maso - Campo da calcio a 5", "Via Maso", "Paesello",
                                "Calcio", "45.7", "11.4"));

        assertThat(ImpiantiVeneto.impianti(righe, PAESELLO, Instant.EPOCH))
                .singleElement()
                .satisfies(impianto -> {
                    assertThat(impianto.nome()).isEqualTo("Impianto Via Maso - Campo da calcio");
                    assertThat(impianto.sport()).containsExactly("Calcio-11", "Calcio-5");
                    assertThat(impianto.lat()).isEqualTo(45.7);
                });
    }

    @Test
    void leDisciplineDiCalcioDelVeneto() {
        assertThat(ImpiantiVeneto.sport("Stadio", "Atletica leggera, calcio a 11, judo", "Stadio Comunale"))
                .containsExactly("Calcio-11");
        assertThat(ImpiantiVeneto.sport("", "Calcio a 5, Calcio a 7", "Centro")).containsExactly("Calcio-5", "Calcio-7");
        assertThat(ImpiantiVeneto.sport("area verde attrezzata calcio", "calcio", "Area comunale"))
                .containsExactly("Calcio");
        assertThat(ImpiantiVeneto.sport("Palestra", "Volley, calcetto bambini", "Palestra Parrocchia"))
                .containsExactly("Calcio-5");
        assertThat(ImpiantiVeneto.sport("Palestra", "Basket, pallavolo", "Palestra Bassani")).isEmpty();
    }

    @Test
    void coordinateEIndirizzi() {
        assertThat(ImpiantiVeneto.posizione("45,7", "11,3")).containsExactly(45.7, 11.3);
        assertThat(ImpiantiVeneto.posizione("11.3", "45.7")).containsExactly(45.7, 11.3);
        assertThat(ImpiantiVeneto.posizione("0", "0")).isNull();
        assertThat(ImpiantiVeneto.posizione("", "11.3")).isNull();
        assertThat(ImpiantiVeneto.indirizzo("via Martiri della Libertà 10", "10")).isEqualTo("via Martiri della Libertà 10");
        assertThat(ImpiantiVeneto.indirizzo("Via Grumetto", "104")).isEqualTo("Via Grumetto 104");
        assertThat(ImpiantiVeneto.indirizzo("Via Maso", "")).isEqualTo("Via Maso");
    }

    @Test
    void ilTestoInUtf8OWindows1252EIlSeparatore() {
        assertThat(LettoreCsv.testo("Spianà".getBytes(StandardCharsets.UTF_8))).isEqualTo("Spianà");
        assertThat(LettoreCsv.testo("Spianà".getBytes(Charset.forName("windows-1252")))).isEqualTo("Spianà");
        assertThat(LettoreCsv.separatore("\"a\";\"b, c\";\"d\"\n1,2,3")).isEqualTo(';');
        assertThat(LettoreCsv.separatore("a,b,c")).isEqualTo(',');
    }
}
