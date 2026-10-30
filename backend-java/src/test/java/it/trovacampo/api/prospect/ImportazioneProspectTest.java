package it.trovacampo.api.prospect;

import static it.trovacampo.api.prospect.RegistroRasdTest.lista;
import static it.trovacampo.api.prospect.RispostaRasd.dettaglio;
import static it.trovacampo.api.prospect.RispostaRasd.elenco;
import static it.trovacampo.api.prospect.RispostaRasd.riga;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * L'importazione contro un registro finto (il client HTTP vero, con le
 * risposte preparate) e un archivio in memoria: paginazione, ripresa,
 * dettagli solo per il calcio laziale, nessun legale rappresentante salvato.
 */
class ImportazioneProspectTest {

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    private long adesso = 0;
    private final List<Long> attese = new ArrayList<>();
    private final RegistroRasd registro =
            new RegistroRasd(
                    RegistroRasd.configura(builder, RegistroRasdTest.URL, RegistroRasdTest.UA).build(),
                    new LimitatoreRichieste(
                            Duration.ofSeconds(1),
                            () -> adesso,
                            millis -> {
                                attese.add(millis);
                                adesso += millis * 1_000_000;
                            }));

    private final Map<String, Prospect> archivio = new LinkedHashMap<>();
    private final Map<Integer, StatoImportazioneProspect> statiSalvati = new LinkedHashMap<>();
    private final ProspectRepository prospect = mock(ProspectRepository.class);
    private final StatiImportazioneRepository stati = mock(StatiImportazioneRepository.class);
    private final CollegamentoCampi collegamento = mock(CollegamentoCampi.class);
    private final ImportazioneProspect importazione =
            new ImportazioneProspect(registro, prospect, stati, collegamento, null);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void archivioInMemoria() {
        when(prospect.findAllById(any()))
                .thenAnswer(
                        inv -> {
                            List<Prospect> trovati = new ArrayList<>();
                            ((Iterable<String>) inv.getArgument(0))
                                    .forEach(cf -> Optional.ofNullable(archivio.get(cf)).ifPresent(trovati::add));
                            return trovati;
                        });
        when(prospect.saveAll(any()))
                .thenAnswer(
                        inv -> {
                            ((Iterable<Prospect>) inv.getArgument(0))
                                    .forEach(p -> archivio.put(p.getCodiceFiscale(), p));
                            return inv.getArgument(0);
                        });
        when(stati.findById(anyInt()))
                .thenAnswer(inv -> Optional.ofNullable(statiSalvati.get((Integer) inv.getArgument(0))));
        when(stati.save(any()))
                .thenAnswer(
                        inv -> {
                            StatoImportazioneProspect s = inv.getArgument(0);
                            statiSalvati.put(s.getRegioneId(), s);
                            return s;
                        });
    }

    private void pagina(int regione, int start, int count, RispostaRasd.Riga... righe) {
        server.expect(requestTo(lista(regione, start)))
                .andRespond(withSuccess(elenco(count, righe), MediaType.APPLICATION_JSON));
    }

    private static ImportazioneProspect.Opzioni lazio(Integer maxPagine, boolean dettagli) {
        return new ImportazioneProspect.Opzioni(List.of(12), maxPagine, false, dettagli);
    }

    @Test
    void leggeTutteLePagineFinoAlTotaleESalvaPerCodiceFiscale() {
        pagina(12, 0, 250, riga(1, "CF1", "A.S.D. UNO", "Roma", "CSI"), riga(2, "cf2", "A.S.D. DUE", "Roma", "UISP"));
        pagina(12, 2, 250, riga(3, "CF3", "A.S.D. TRE", "Latina", "FIDAL"));
        // Il registro ne dichiara 250 ma la terza pagina è vuota: ci si ferma lì.
        pagina(12, 3, 250);

        ImportazioneProspect.Avanzamento esito = importazione.esegui(lazio(null, false));

        assertThat(esito.stato()).isEqualTo("completata");
        assertThat(esito.pagine()).isEqualTo(3);
        assertThat(archivio).containsOnlyKeys("CF1", "CF2", "CF3");
        assertThat(archivio.get("CF2").getOrganismi()).containsExactly("UISP");
        assertThat(statiSalvati.get(12).isCompletata()).isTrue();
        // Lazio fatto: si collegano i campi.
        verify(collegamento).collega();
        server.verify();
    }

    @Test
    void riparteDallUltimoStartSalvato() {
        statiSalvati.put(12, new StatoImportazioneProspect(12, "Lazio").setProssimoStart(200).setTotale(250));
        pagina(12, 200, 250, riga(201, "CF201", "A.S.D. DUECENTOUNO", "Rieti", "CSI"));
        pagina(12, 201, 250);

        importazione.esegui(lazio(null, false));

        // Nessuna richiesta per start=0 o 100: il server finto le rifiuterebbe.
        assertThat(archivio).containsOnlyKeys("CF201");
        server.verify();
    }

    @Test
    void conIlLimiteDiPagineSiFermaESalvaDoveRiprendere() {
        pagina(12, 0, 5, riga(1, "CF1", "UNO", "Roma", "CSI"), riga(2, "CF2", "DUE", "Roma", "CSI"));

        ImportazioneProspect.Avanzamento esito = importazione.esegui(lazio(1, false));

        assertThat(esito.stato()).isEqualTo("pausa");
        assertThat(statiSalvati.get(12).getProssimoStart()).isEqualTo(2);
        assertThat(statiSalvati.get(12).isCompletata()).isFalse();
        server.verify();
    }

    @Test
    void unaRegioneGiaCompletataSiSaltaSalvoRicominciare() {
        statiSalvati.put(12, new StatoImportazioneProspect(12, "Lazio").setCompletata(true));
        server.expect(ExpectedCount.never(), requestTo(lista(12, 0)));

        importazione.esegui(lazio(null, false));

        server.verify();
        verify(collegamento, never()).collega();
    }

    @Test
    void ilDettaglioSoloPerIlCalcioDelLazioESenzaLegaleRappresentante() throws Exception {
        pagina(
                12, 0, 2,
                riga(10, "CFCALCIO", "A.S.D. VIS SUBIACO", "Subiaco", "FIGC", "CSI"),
                riga(11, "CFSCHERMA", "CLUB SCHERMA ROMA A.S.D.", "Roma", "FIS"));
        server.expect(requestTo(RegistroRasdTest.URL + "/api/istruttoria/10/sidebar/?chiamante=registroPubblico_n"))
                .andRespond(withSuccess(dettaglio("CFCALCIO", "Calcio a 11, Calcio a 5", 180), MediaType.APPLICATION_JSON));

        importazione.esegui(lazio(null, true));

        Prospect calcio = archivio.get("CFCALCIO");
        assertThat(calcio.isCalcio()).isTrue();
        assertThat(calcio.getTesseratiAttivi()).isEqualTo(180);
        assertThat(calcio.getDiscipline()).containsExactly("Calcio a 11", "Calcio a 5");
        assertThat(calcio.getDettaglioLetto()).isNotNull();
        assertThat(archivio.get("CFSCHERMA").isCalcio()).isFalse();
        assertThat(archivio.get("CFSCHERMA").getDettaglioLetto()).isNull();
        // Due richieste (elenco e un dettaglio): un'attesa da un secondo.
        assertThat(attese).containsExactly(1000L);
        server.verify();

        // Quello che finisce in Mongo, campo per campo: il nome non c'è.
        String salvato =
                new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(archivio.values());
        assertThat(salvato).doesNotContain(RispostaRasd.LEGALE_RAPPRESENTANTE);
    }

    @Test
    void unDettaglioGiaLettoNonSiRichiede() {
        archivio.put(
                "CFCALCIO",
                new Prospect().setCodiceFiscale("CFCALCIO").setTesseratiAttivi(99)
                        .setDettaglioLetto(java.time.Instant.parse("2026-10-01T10:00:00Z")));
        pagina(12, 0, 1, riga(10, "CFCALCIO", "A.S.D. VIS SUBIACO", "Subiaco", "FIGC"));

        importazione.esegui(lazio(null, true));

        assertThat(archivio.get("CFCALCIO").getTesseratiAttivi()).isEqualTo(99);
        server.verify();
    }

    @Test
    void seIlRegistroBloccaSiFermaELoDice() {
        pagina(12, 0, 300, riga(1, "CF1", "UNO", "Roma", "CSI"));
        server.expect(requestTo(lista(12, 1))).andRespond(withStatus(HttpStatus.FORBIDDEN));

        ImportazioneProspect.Avanzamento esito = importazione.esegui(lazio(null, false));

        assertThat(esito.stato()).isEqualTo("bloccata");
        assertThat(esito.errore()).contains("403");
        // Si riprende dalla pagina rifiutata, non da capo.
        assertThat(statiSalvati.get(12).getProssimoStart()).isEqualTo(1);
        server.verify();
    }

    @Test
    void calcioVuolDireFigcOUnaDisciplinaDiCalcio() {
        assertThat(ImportazioneProspect.calcio(List.of("CSI", "FIGC"), null)).isTrue();
        assertThat(ImportazioneProspect.calcio(List.of("CSI"), List.of("Calcio a 5"))).isTrue();
        assertThat(ImportazioneProspect.calcio(List.of("FIS"), List.of("Sciabola"))).isFalse();
    }
}
