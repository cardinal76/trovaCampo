package it.trovacampo.api.prospect;

import static it.trovacampo.api.prospect.RegistroRasdTest.lista;
import static it.trovacampo.api.prospect.RispostaRasd.dettaglio;
import static it.trovacampo.api.prospect.RispostaRasd.elenco;
import static it.trovacampo.api.prospect.RispostaRasd.riga;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import it.trovacampo.api.service.LimitatoreRichieste;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

    private static final ZoneId ROMA = ZoneId.of("Europe/Rome");
    /** Il 15 ottobre 2026 a mezzogiorno, ora italiana. */
    static final Instant BASE = ZonedDateTime.of(2026, 10, 15, 12, 0, 0, 0, ROMA).toInstant();

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    /** Un solo orologio finto, in nanosecondi da BASE, per il limitatore e per il ritmo. */
    private long adesso = 0;
    private final List<Long> attese = new ArrayList<>();
    private final LimitatoreRichieste.Attesa dormi =
            millis -> {
                attese.add(millis);
                adesso += millis * 1_000_000;
            };
    private final Clock orologio = new OrologioFinto();
    private final RegistroRasd registro =
            new RegistroRasd(
                    RegistroRasd.configura(builder, RegistroRasdTest.URL, RegistroRasdTest.UA).build(),
                    new LimitatoreRichieste(Duration.ofSeconds(1), () -> adesso, dormi));

    private final Map<String, Prospect> archivio = new LinkedHashMap<>();
    private final Map<Integer, StatoImportazioneProspect> statiSalvati = new LinkedHashMap<>();
    private final Map<String, GiroImportazione> giroSalvato = new LinkedHashMap<>();
    /** Le fasi scritte in Mongo, in ordine, senza ripetizioni consecutive. */
    private final List<String> fasi = new ArrayList<>();
    private final ProspectRepository prospect = mock(ProspectRepository.class);
    private final StatiImportazioneRepository stati = mock(StatiImportazioneRepository.class);
    private final GiriImportazioneRepository giri = mock(GiriImportazioneRepository.class);
    private final CollegamentoCampi collegamento = mock(CollegamentoCampi.class);
    /** Di default il ritmo più semplice: un secondo, niente jitter, finestra o tetto. */
    private ImportazioneProspect importazione = conRitmo(new RitmoRasd.Configurazione(Duration.ofSeconds(1), Duration.ZERO, null, null, ROMA, 0));

    private final class OrologioFinto extends Clock {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return BASE.plusNanos(adesso);
        }
    }

    private ImportazioneProspect conRitmo(RitmoRasd.Configurazione configurazione) {
        RitmoRasd ritmo = new RitmoRasd(configurazione, orologio, dormi::dormi, massimo -> massimo);
        return new ImportazioneProspect(
                registro, prospect, stati, giri, collegamento, ritmo, Duration.ofDays(30), null);
    }

    private static RitmoRasd.Configurazione ritmo(String finestra, int tetto) {
        return RitmoRasd.Configurazione.di(1000, 0, finestra, "Europe/Rome", tetto);
    }

    private ZonedDateTime aRoma() {
        return orologio.instant().atZone(ROMA);
    }

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
        when(giri.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(giroSalvato.get((String) inv.getArgument(0))));
        when(giri.save(any()))
                .thenAnswer(
                        inv -> {
                            GiroImportazione g = inv.getArgument(0);
                            giroSalvato.put(g.getId(), g);
                            if (fasi.isEmpty() || !fasi.get(fasi.size() - 1).equals(g.getFase())) {
                                fasi.add(g.getFase());
                            }
                            return g;
                        });
        when(stati.findAll()).thenAnswer(inv -> new ArrayList<>(statiSalvati.values()));
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
        return new ImportazioneProspect.Opzioni(
                List.of(12), maxPagine, false, dettagli ? AmbitoDettagli.CALCIO : AmbitoDettagli.NESSUNO);
    }

    /** Una pagina dell'elenco che si segna l'ora (italiana) in cui è stata chiesta. */
    private void paginaAlle(List<LocalTime> ore, int regione, int start, int count, RispostaRasd.Riga... righe) {
        server.expect(requestTo(lista(regione, start)))
                .andRespond(
                        richiesta -> {
                            ore.add(aRoma().toLocalTime());
                            return withSuccess(elenco(count, righe), MediaType.APPLICATION_JSON).createResponse(richiesta);
                        });
    }

    private void dettaglioDi(long id, String cf) {
        server.expect(requestTo(RegistroRasdTest.URL + "/api/istruttoria/" + id + "/sidebar/?chiamante=registroPubblico_n"))
                .andRespond(withSuccess(dettaglio(cf, "Calcio a 11", 50), MediaType.APPLICATION_JSON));
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

    // --- Ritmo: finestra oraria, tetto giornaliero, blocchi, ripresa ---

    @Test
    void fuoriDallaFinestraAspettaLaSeraESenzaPerdereIlPostoSiFermaAlleSette() {
        importazione = conRitmo(ritmo("22:00-07:00", 0));
        List<LocalTime> ore = new ArrayList<>();
        paginaAlle(ore, 12, 0, 300, riga(1, "CF1", "UNO", "Roma", "CSI"));
        paginaAlle(ore, 12, 1, 300, riga(2, "CF2", "DUE", "Roma", "CSI"));
        paginaAlle(ore, 12, 2, 3, riga(3, "CF3", "TRE", "Roma", "CSI"));

        // A mezzogiorno non si chiede niente: si aspetta le 22.
        ImportazioneProspect.Avanzamento esito = importazione.esegui(lazio(null, false));

        assertThat(esito.stato()).isEqualTo("completata");
        assertThat(ore).containsExactly(LocalTime.of(22, 0), LocalTime.of(22, 0, 1), LocalTime.of(22, 0, 2));
        assertThat(fasi).containsSubsequence("in corso", "in pausa: finestra oraria", "in corso", "completata");
    }

    @Test
    void allaChiusuraDellaFinestraSiMetteInPausaERiparteLaSeraDopoDallaStessaPagina() {
        // Alle 6:59:58 si è dentro; alle 7:00:00 fuori, fino alle 22.
        adesso = Duration.between(BASE, ZonedDateTime.of(2026, 10, 16, 6, 59, 58, 0, ROMA).toInstant()).toNanos();
        importazione = conRitmo(ritmo("22:00-07:00", 0));
        List<LocalTime> ore = new ArrayList<>();
        paginaAlle(ore, 12, 0, 300, riga(1, "CF1", "UNO", "Roma", "CSI"));
        paginaAlle(ore, 12, 1, 300, riga(2, "CF2", "DUE", "Roma", "CSI"));
        paginaAlle(ore, 12, 2, 3, riga(3, "CF3", "TRE", "Roma", "CSI"));

        importazione.esegui(lazio(null, false));

        assertThat(ore).containsExactly(LocalTime.of(6, 59, 58), LocalTime.of(6, 59, 59), LocalTime.of(22, 0));
        assertThat(aRoma().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(archivio).containsOnlyKeys("CF1", "CF2", "CF3");
        // Le pause lunghe si fanno a pezzi di un minuto: un «Ferma» arriva in fretta.
        assertThat(attese).allMatch(ms -> ms <= 60_000);
        server.verify();
    }

    @Test
    void fermarloDuranteLaPausaDellaFinestraNonFaRichieste() {
        // Il primo «sonno» della pausa arriva insieme a un Ferma.
        ImportazioneProspect[] questa = new ImportazioneProspect[1];
        questa[0] =
                new ImportazioneProspect(
                        registro, prospect, stati, giri, collegamento,
                        new RitmoRasd(ritmo("22:00-07:00", 0), orologio, ms -> questa[0].ferma(), m -> m),
                        Duration.ofDays(30), null);

        ImportazioneProspect.Avanzamento esito = questa[0].esegui(lazio(null, false));

        assertThat(esito.stato()).isEqualTo("fermata");
        assertThat(esito.richieste()).isZero();
        assertThat(giroSalvato.get("rasd").getFase()).isEqualTo("fermata");
        server.verify();
    }

    @Test
    void ilTettoGiornalieroRimandaAMezzanotteELaPosizioneResta() {
        importazione = conRitmo(ritmo("", 2));
        List<LocalTime> ore = new ArrayList<>();
        paginaAlle(ore, 12, 0, 300, riga(1, "CF1", "UNO", "Roma", "CSI"));
        paginaAlle(ore, 12, 1, 300, riga(2, "CF2", "DUE", "Roma", "CSI"));
        paginaAlle(ore, 12, 2, 3, riga(3, "CF3", "TRE", "Roma", "CSI"));

        ImportazioneProspect.Avanzamento esito = importazione.esegui(lazio(null, false));

        assertThat(esito.stato()).isEqualTo("completata");
        assertThat(ore).containsExactly(LocalTime.of(12, 0), LocalTime.of(12, 0, 1), LocalTime.MIDNIGHT);
        assertThat(aRoma().toLocalDate()).isEqualTo(LocalDate.of(2026, 10, 16));
        assertThat(fasi).contains("in pausa: tetto giornaliero");
        // Il contatore di oggi è in Mongo: il 16 ottobre una richiesta.
        assertThat(giroSalvato.get("rasd").getGiorno()).isEqualTo("2026-10-16");
        assertThat(giroSalvato.get("rasd").getRichiesteGiorno()).isEqualTo(1);
        server.verify();
    }

    @Test
    void ilContatoreDiOggiSopravviveAUnRiavvio() {
        // Prima del riavvio se n'erano già fatte due, il tetto è due: si aspetta domani.
        giroSalvato.put("rasd", new GiroImportazione().setFase("fermata").setGiorno("2026-10-15").setRichiesteGiorno(2));
        importazione = conRitmo(ritmo("", 2));
        List<LocalTime> ore = new ArrayList<>();
        paginaAlle(ore, 12, 0, 1, riga(1, "CF1", "UNO", "Roma", "CSI"));

        assertThat(importazione.stato().richiesteOggi()).isEqualTo(2);
        importazione.esegui(lazio(null, false));

        assertThat(ore).containsExactly(LocalTime.MIDNIGHT);
        server.verify();
    }

    @Test
    void al429SiFermaSubitoNonRiprovaELoDiceAncheDopoUnRiavvio() {
        pagina(12, 0, 300, riga(1, "CF1", "UNO", "Roma", "CSI"));
        server.expect(ExpectedCount.once(), requestTo(lista(12, 1))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        ImportazioneProspect.Avanzamento esito = importazione.esegui(lazio(null, false));

        assertThat(esito.stato()).isEqualTo("bloccata");
        assertThat(esito.errore()).contains("429");
        assertThat(esito.richieste()).isEqualTo(2);
        // Una sola volta: il server finto rifiuterebbe un secondo tentativo.
        server.verify();
        assertThat(statiSalvati.get(12).getProssimoStart()).isEqualTo(1);
        // Niente collegamento ai campi con il registro che blocca.
        verify(collegamento, never()).collega();

        // Dopo un riavvio (un'altra istanza) la pagina lo dice ancora, e aspetta una persona.
        ImportazioneProspect.Stato stato = conRitmo(ritmo("", 0)).stato();
        assertThat(stato.inCorso()).isFalse();
        assertThat(stato.bloccata()).isTrue();
        assertThat(stato.fase()).isEqualTo("bloccata");
        assertThat(stato.motivo()).contains("429");
        assertThat(stato.bloccataIl()).isNotNull();
        assertThat(stato.riprendibile()).isTrue();
    }

    @Test
    void unaPaginaHtmlAMetaDeiDettagliFermaTuttoETieneQuelliGiaLetti() {
        pagina(
                12, 0, 2,
                riga(10, "CFA", "A.S.D. A", "Roma", "FIGC"),
                riga(11, "CFB", "A.S.D. B", "Roma", "FIGC"));
        dettaglioDi(10, "CFA");
        server.expect(ExpectedCount.once(), requestTo(RegistroRasdTest.URL + "/api/istruttoria/11/sidebar/?chiamante=registroPubblico_n"))
                .andRespond(withSuccess("<html>captcha</html>", MediaType.TEXT_HTML));

        ImportazioneProspect.Avanzamento esito = importazione.esegui(lazio(null, true));

        assertThat(esito.stato()).isEqualTo("bloccata");
        assertThat(archivio.get("CFA").getDettaglioLetto()).isNotNull();
        server.verify();
    }

    @Test
    void riprendeLUltimoGiroConLeSueOpzioniDallaPosizioneSalvata() {
        // Il backend si è riavviato mentre il giro era in pausa per la finestra.
        giroSalvato.put(
                "rasd",
                new GiroImportazione().setRegioni(List.of(12, 13)).setDettagli(AmbitoDettagli.FIGC)
                        .setFase("in pausa: finestra oraria"));
        statiSalvati.put(12, new StatoImportazioneProspect(12, "Lazio").setCompletata(true).setTotale(12070));
        statiSalvati.put(13, new StatoImportazioneProspect(13, "Abruzzo").setProssimoStart(100).setTotale(102));

        ImportazioneProspect.Stato prima = importazione.stato();
        assertThat(prima.fase()).isEqualTo("interrotta");
        assertThat(prima.riprendibile()).isTrue();
        // Restano due società dell'Abruzzo: una pagina, e il 12% di dettagli FIGC (arrotondato a zero).
        assertThat(prima.stima().pagine()).isEqualTo(1);

        pagina(13, 100, 102, riga(201, "CF201", "A.S.D. PESCARA", "Pescara", "FIGC"), riga(202, "CF202", "SCHERMA", "Chieti", "FIS"));
        dettaglioDi(201, "CF201");

        ImportazioneProspect.Opzioni opzioni = importazione.daRiprendere().orElseThrow();
        assertThat(opzioni).isEqualTo(new ImportazioneProspect.Opzioni(List.of(12, 13), null, false, AmbitoDettagli.FIGC));
        ImportazioneProspect.Avanzamento esito = importazione.esegui(opzioni);

        assertThat(esito.stato()).isEqualTo("completata");
        assertThat(archivio).containsOnlyKeys("CF201", "CF202");
        assertThat(archivio.get("CF201").getDettaglioLetto()).isNotNull();
        assertThat(archivio.get("CF202").getDettaglioLetto()).isNull();
        assertThat(statiSalvati.get(13).isCompletata()).isTrue();
        server.verify();
    }

    @Test
    void conTutteIDettagliSiSaltanoQuelliLettiNegliUltimiTrentaGiorni() {
        archivio.put("CFRECENTE", new Prospect().setCodiceFiscale("CFRECENTE").setTesseratiAttivi(7)
                .setDettaglioLetto(BASE.minus(Duration.ofDays(10))));
        archivio.put("CFVECCHIO", new Prospect().setCodiceFiscale("CFVECCHIO").setTesseratiAttivi(7)
                .setDettaglioLetto(BASE.minus(Duration.ofDays(40))));
        pagina(
                15, 0, 3,
                riga(1, "CFRECENTE", "RECENTE", "Napoli", "UISP"),
                riga(2, "CFVECCHIO", "VECCHIO", "Napoli", "CSI"),
                riga(3, "CFNUOVO", "NUOVO", "Napoli", "FIS"));
        dettaglioDi(2, "CFVECCHIO");
        dettaglioDi(3, "CFNUOVO");

        ImportazioneProspect.Avanzamento esito =
                importazione.esegui(new ImportazioneProspect.Opzioni(List.of(15), null, false, AmbitoDettagli.TUTTE));

        assertThat(esito.dettagli()).isEqualTo(2);
        assertThat(esito.dettagliSaltati()).isEqualTo(1);
        assertThat(archivio.get("CFRECENTE").getTesseratiAttivi()).isEqualTo(7);
        assertThat(archivio.get("CFVECCHIO").getTesseratiAttivi()).isEqualTo(50);
        assertThat(archivio.get("CFNUOVO").getTesseratiAttivi()).isEqualTo(50);
        // Fuori dal Lazio non si collega niente.
        verify(collegamento, never()).collega();
        server.verify();
    }

    @Test
    void laPausaHaIlSuoJitter() {
        importazione = conRitmo(RitmoRasd.Configurazione.di(3000, 1000, "", "Europe/Rome", 0));
        pagina(12, 0, 2, riga(1, "CF1", "UNO", "Roma", "CSI"));
        pagina(12, 1, 2, riga(2, "CF2", "DUE", "Roma", "CSI"));

        importazione.esegui(lazio(null, false));

        // Il caso finto dà sempre il massimo: 3 secondi più 1 di jitter.
        assertThat(attese).containsExactly(4000L);
        server.verify();
    }

    @Test
    void laStimaTieneContoDiFinestraETetto() {
        RitmoRasd.Configurazione prudente = RitmoRasd.Configurazione.di(3000, 1000, "22:00-07:00", "Europe/Rome", 7000);
        // 3,5 s di pausa media più un secondo di risposta: 7.200 a notte, il tetto ne lascia 7.000.
        assertThat(prudente.richiestePerGiorno()).isEqualTo(7000);
        // Solo calcio: ~1.000 pagine di elenco + ~12.000 dettagli.
        assertThat(prudente.stima(13_000).toHours() / 24.0).isBetween(1.8, 1.9);
        // Tutto: ~1.000 pagine + ~100.000 dettagli.
        assertThat(prudente.stima(101_000).toDays()).isEqualTo(14);
        // Senza finestra né tetto è il ritmo puro: 4,5 s a richiesta.
        assertThat(RitmoRasd.Configurazione.di(3000, 1000, "", "Europe/Rome", 0).stima(800).toSeconds()).isEqualTo(3600);
    }

    @Test
    void calcioVuolDireFigcOUnaDisciplinaDiCalcio() {
        assertThat(ImportazioneProspect.calcio(List.of("CSI", "FIGC"), null)).isTrue();
        assertThat(ImportazioneProspect.calcio(List.of("CSI"), List.of("Calcio a 5"))).isTrue();
        assertThat(ImportazioneProspect.calcio(List.of("FIS"), List.of("Sciabola"))).isFalse();
    }
}
