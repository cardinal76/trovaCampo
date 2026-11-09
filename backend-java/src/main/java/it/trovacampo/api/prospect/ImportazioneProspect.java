package it.trovacampo.api.prospect;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/**
 * L'importazione del Registro nazionale (RASD) nella collection
 * {@code prospect}, lanciata a mano dall'amministrazione: niente giri
 * programmati, e niente riprese automatiche dopo un blocco.
 *
 * <ul>
 *   <li>legge l'elenco una regione alla volta, a pagine di {@link #PAGINA}
 *       righe, al ritmo di {@link RitmoRasd}: pausa con jitter, finestra
 *       oraria, tetto giornaliero;
 *   <li>salva o aggiorna per codice fiscale: rileggere una pagina non crea
 *       doppioni, e i dati già arricchiti (dettaglio, campo) restano;
 *   <li>dopo ogni pagina salva il prossimo {@code start} della regione
 *       ({@link StatoImportazioneProspect}): fermata o interrotta, riparte da
 *       lì. Con {@code ricomincia} si riparte da zero;
 *   <li>chiede il dettaglio (discipline, tesserati) per le società
 *       dell'{@link AmbitoDettagli} scelto, in tutte le regioni del giro, e
 *       lo salta se è più recente di {@code dettaglio-valido-giorni};
 *   <li>al primo segno di blocco (403, 429, 503, una pagina HTML) si ferma,
 *       lo scrive in {@link GiroImportazione} e aspetta una persona;
 *   <li>se ha toccato il Lazio, collega i prospect ai campi ({@link CollegamentoCampi}).
 * </ul>
 *
 * <p>Gira in un thread suo, uno solo per volta: un'importazione nazionale
 * dura giorni, una richiesta HTTP no.
 */
@EnableConfigurationProperties(ProprietaRasd.class)
@Service
public class ImportazioneProspect {

    private static final Logger log = LoggerFactory.getLogger(ImportazioneProspect.class);

    static final int PAGINA = 100;
    static final int LAZIO = 12;

    static final String IN_CORSO = "in corso";
    static final String PAUSA_FINESTRA = "in pausa: finestra oraria";
    static final String PAUSA_TETTO = "in pausa: tetto giornaliero";
    static final String BLOCCATA = "bloccata";
    static final String INTERROTTA = "interrotta";

    private final RegistroRasd registro;
    private final ProspectRepository prospect;
    private final StatiImportazioneRepository stati;
    private final GiriImportazioneRepository giri;
    private final CollegamentoCampi collegamento;
    private final RitmoRasd ritmo;
    private final Duration validitaDettaglio;
    private final ExecutorService esecutore;

    private final AtomicBoolean inCorso = new AtomicBoolean(false);
    private volatile boolean fermati = false;
    private volatile AvanzamentoImportazione ultimo = AvanzamentoImportazione.mai();
    private volatile String fase = IN_CORSO;
    private volatile Instant pausaFinoA;
    private volatile GiroImportazione giro;

    @Autowired
    public ImportazioneProspect(
            RegistroRasd registro,
            ProspectRepository prospect,
            StatiImportazioneRepository stati,
            GiriImportazioneRepository giri,
            CollegamentoCampi collegamento,
            RitmoRasd ritmo,
            ProprietaRasd proprieta) {
        this(
                registro,
                prospect,
                stati,
                giri,
                collegamento,
                ritmo,
                Duration.ofDays(proprieta.dettaglioValidoGiorni()),
                Executors.newSingleThreadExecutor(
                        r -> {
                            Thread t = new Thread(r, "importazione-prospect");
                            t.setDaemon(true);
                            return t;
                        }));
    }

    ImportazioneProspect(
            RegistroRasd registro,
            ProspectRepository prospect,
            StatiImportazioneRepository stati,
            GiriImportazioneRepository giri,
            CollegamentoCampi collegamento,
            RitmoRasd ritmo,
            Duration validitaDettaglio,
            ExecutorService esecutore) {
        this.registro = registro;
        this.prospect = prospect;
        this.stati = stati;
        this.giri = giri;
        this.collegamento = collegamento;
        this.ritmo = ritmo;
        this.validitaDettaglio = validitaDettaglio;
        this.esecutore = esecutore;
    }

    public StatoImportazione stato() {
        boolean corre = inCorso.get();
        GiroImportazione g = corre && giro != null ? giro : giri.findById(GiroImportazione.ID).orElse(null);
        String faseOra;
        if (corre) {
            faseOra = fase;
        } else if (g == null || g.getFase() == null) {
            faseOra = "mai";
        } else if (g.getFase().equals(IN_CORSO) || g.getFase().startsWith("in pausa")) {
            // Il backend si è riavviato a metà: si riprende a mano.
            faseOra = INTERROTTA;
        } else {
            faseOra = g.getFase();
        }
        if (g != null && g.getGiorno() != null && !corre) {
            ritmo.ripristina(LocalDate.parse(g.getGiorno()), g.getRichiesteGiorno());
        }
        OpzioniImportazione opzioni = g == null ? null : new OpzioniImportazione(g.getRegioni(), null, false, g.getDettagli());
        List<StatoImportazioneProspect> regioni = stati.findAll();
        RitmoRasd.Configurazione c = ritmo.configurazione();
        return new StatoImportazione(
                corre,
                faseOra,
                corre ? pausaFinoA : null,
                corre ? ultimo.regioneCorrente() : null,
                ritmo.richiesteOggi(),
                BLOCCATA.equals(faseOra),
                g == null ? null : g.getMotivo(),
                g == null ? null : g.getBloccataIl(),
                !corre && g != null && !"completata".equals(faseOra),
                opzioni,
                RitmoImportazione.di(c),
                opzioni == null ? null : StimaImportazione.di(opzioni, regioni, c, PAGINA),
                ultimo,
                regioni);
    }

    /** Parte in sottofondo. Falso se ce n'è già una in corso. */
    public boolean avvia(OpzioniImportazione opzioni, String chi) {
        if (!inCorso.compareAndSet(false, true)) {
            return false;
        }
        fermati = false;
        fase = IN_CORSO;
        pausaFinoA = null;
        ultimo = new AvanzamentoImportazione(IN_CORSO, adesso(), null, 0, 0, 0, 0, 0, 0, null, null, null);
        log.info("Importazione del registro RASD avviata da {}: {}", chi, opzioni);
        esecutore.submit(
                () -> {
                    try {
                        esegui(opzioni, chi);
                    } finally {
                        inCorso.set(false);
                    }
                });
        return true;
    }

    /** Le opzioni dell'ultimo giro, per riprenderlo da dove si era fermato. */
    public Optional<OpzioniImportazione> daRiprendere() {
        return giri.findById(GiroImportazione.ID)
                .filter(g -> g.getFase() != null)
                .map(g -> new OpzioniImportazione(g.getRegioni(), null, false, g.getDettagli()));
    }

    /**
     * Riprende l'ultimo giro con le sue opzioni: l'unico modo di ripartire
     * dopo un blocco, perché da solo non riprova. Vuoto se non c'è niente da
     * riprendere, falso se ce n'è già uno in corso.
     */
    public Optional<Boolean> riprendi(String chi) {
        return daRiprendere().map(opzioni -> avvia(opzioni, chi));
    }

    /** Chiede di fermarsi alla fine della richiesta in corso (o della pausa): si riprende da lì. */
    public void ferma() {
        fermati = true;
    }

    private Instant adesso() {
        return ritmo.orologio().instant();
    }

    AvanzamentoImportazione esegui(OpzioniImportazione opzioni) {
        return esegui(opzioni, null);
    }

    /**
     * Il giro vero, nel thread di chi chiama: lo usa {@link #avvia} e lo
     * usano i test.
     */
    AvanzamentoImportazione esegui(OpzioniImportazione opzioni, String chi) {
        Contatori c = new Contatori();
        Instant iniziato = adesso();
        giro = giri.findById(GiroImportazione.ID).orElseGet(GiroImportazione::new);
        if (giro.getGiorno() != null) {
            ritmo.ripristina(LocalDate.parse(giro.getGiorno()), giro.getRichiesteGiorno());
        }
        ritmo.nuovoGiro();
        giro.setRegioni(opzioni.regioni())
                .setDettagli(opzioni.dettagli())
                .setAvviatoDa(chi)
                .setIniziato(iniziato)
                .setFase(IN_CORSO)
                .setMotivo(null)
                .setBloccataIl(null);
        salvaGiro();

        String regioneCorrente = null;
        String errore = null;
        String esito = "completata";
        boolean lazioToccato = false;
        try {
            for (Integer regioneId : opzioni.regioniDaFare()) {
                String nome = RegistroRasd.REGIONI.getOrDefault(regioneId, "regione " + regioneId);
                regioneCorrente = nome;
                StatoImportazioneProspect stato =
                        stati.findById(regioneId).orElseGet(() -> new StatoImportazioneProspect(regioneId, nome));
                if (opzioni.ricomincia()) {
                    stato.setProssimoStart(0).setCompletata(false);
                }
                if (stato.isCompletata()) {
                    continue;
                }
                lazioToccato |= regioneId == LAZIO;
                boolean finita = importaRegione(stato, opzioni, c);
                if (!finita) {
                    esito = fermati ? "fermata" : "pausa";
                    break;
                }
            }
        } catch (RegistroRasd.RegistroBloccatoException e) {
            esito = BLOCCATA;
            errore = e.getMessage();
            giro.setBloccataIl(adesso());
            log.warn("Importazione del registro RASD fermata: {}", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            esito = "fermata";
        } catch (RuntimeException e) {
            esito = "errore";
            errore = e.getMessage();
            log.error("Importazione del registro RASD fallita", e);
        }

        CollegamentoCampi.Esito collegati = null;
        if (lazioToccato && !BLOCCATA.equals(esito)) {
            try {
                collegati = collegamento.collega();
            } catch (RuntimeException e) {
                log.error("Collegamento dei prospect ai campi fallito", e);
            }
        }
        giro.setFase(esito).setMotivo(errore);
        salvaGiro();
        pausaFinoA = null;
        AvanzamentoImportazione fatto =
                new AvanzamentoImportazione(
                        esito, iniziato, adesso(), c.pagine, c.richieste, c.lette, c.nuove,
                        c.dettagli, c.saltati, regioneCorrente, errore, collegati);
        ultimo = fatto;
        log.info(
                "Importazione del registro RASD {}: {} pagine, {} società lette ({} nuove), {} dettagli ({} saltati perché recenti)",
                esito, c.pagine, c.lette, c.nuove, c.dettagli, c.saltati);
        return fatto;
    }

    private void salvaGiro() {
        giro.setGiorno(String.valueOf(ritmo.giorno()))
                .setRichiesteGiorno(ritmo.richiesteOggi())
                .setAggiornato(adesso());
        giri.save(giro);
    }

    private static final class Contatori {
        int pagine;
        int richieste;
        int lette;
        int nuove;
        int dettagli;
        int saltati;
    }

    /** Le pause del ritmo finiscono nella pagina admin e in Mongo. */
    private final RitmoRasd.Ascoltatore ascoltatore =
            new RitmoRasd.Ascoltatore() {
                @Override
                public void inPausa(RitmoRasd.Motivo motivo, Instant finoA) {
                    String nuova = motivo == RitmoRasd.Motivo.FINESTRA ? PAUSA_FINESTRA : PAUSA_TETTO;
                    if (!nuova.equals(fase) || !finoA.equals(pausaFinoA)) {
                        log.info("Importazione del registro RASD {} fino a {}", nuova, finoA);
                        fase = nuova;
                        pausaFinoA = finoA;
                        giro.setFase(nuova);
                        salvaGiro();
                    }
                }

                @Override
                public void inCorso() {
                    fase = IN_CORSO;
                    pausaFinoA = null;
                    giro.setFase(IN_CORSO);
                    salvaGiro();
                }
            };

    /** Aspetta il ritmo e conta la richiesta. Falso se nel frattempo è arrivato un «Ferma». */
    private boolean prossimaRichiesta(Contatori c) throws InterruptedException {
        if (!ritmo.prima(() -> fermati, ascoltatore)) {
            return false;
        }
        c.richieste++;
        salvaGiro();
        return true;
    }

    /** Vero se la regione è arrivata in fondo, falso se ci si è fermati prima. */
    private boolean importaRegione(StatoImportazioneProspect stato, OpzioniImportazione opzioni, Contatori c)
            throws InterruptedException {
        while (true) {
            if (fermati || (opzioni.maxPagine() != null && c.pagine >= opzioni.maxPagine())) {
                return false;
            }
            if (!prossimaRichiesta(c)) {
                return false;
            }
            int start = stato.getProssimoStart();
            RegistroRasd.Pagina pagina = registro.pagina(stato.getRegioneId(), start, PAGINA);
            c.pagine++;
            boolean completa = aggiorna(c, pagina.voci(), opzioni.dettagli());
            if (!completa) {
                // Fermati a metà dei dettagli: la pagina si rilegge al prossimo giro,
                // e i dettagli già letti si saltano perché recenti.
                return false;
            }

            int prossimo = start + pagina.voci().size();
            boolean finita = pagina.voci().isEmpty() || prossimo >= pagina.totale();
            stato.setProssimoStart(finita ? 0 : prossimo)
                    .setTotale(pagina.totale())
                    .setCompletata(finita)
                    .setAggiornato(adesso());
            stati.save(stato);
            ultimo =
                    new AvanzamentoImportazione(
                            IN_CORSO, ultimo.iniziato(), null, c.pagine, c.richieste, c.lette,
                            c.nuove, c.dettagli, c.saltati, stato.getRegione(), null, null);
            if (finita) {
                return true;
            }
        }
    }

    /** Un dettaglio letto da meno di {@code validitaDettaglio} non si richiede. */
    private boolean recente(Prospect p) {
        return p.getDettaglioLetto() != null && p.getDettaglioLetto().isAfter(adesso().minus(validitaDettaglio));
    }

    /** Falso se ci si è fermati prima di leggere tutti i dettagli della pagina. */
    private boolean aggiorna(Contatori c, List<RegistroRasd.Voce> voci, AmbitoDettagli ambito)
            throws InterruptedException {
        if (voci.isEmpty()) {
            return true;
        }
        boolean completa = true;
        Map<String, Prospect> esistenti =
                prospect.findAllById(voci.stream().map(RegistroRasd.Voce::codiceFiscale).toList())
                        .stream()
                        .collect(Collectors.toMap(Prospect::getCodiceFiscale, Function.identity(), (a, b) -> a));
        List<Prospect> daSalvare = new ArrayList<>();
        try {
            for (RegistroRasd.Voce voce : voci) {
                Prospect p = esistenti.get(voce.codiceFiscale());
                if (p == null) {
                    p = new Prospect().setCodiceFiscale(voce.codiceFiscale());
                    c.nuove++;
                }
                p.setRasdId(voce.id())
                        .setDenominazione(voce.denominazione())
                        .setNaturaGiuridica(voce.naturaGiuridica())
                        .setRegione(voce.regione())
                        .setComune(voce.comune())
                        .setOrganismi(voce.organismi())
                        .setDataIscrizione(voce.dataIscrizione())
                        .setCalcio(calcio(voce.organismi(), p.getDiscipline()))
                        .setAggiornato(adesso());
                if (completa && ambito.vuole(voce, p)) {
                    if (recente(p)) {
                        c.saltati++;
                    } else if (!prossimaRichiesta(c)) {
                        completa = false;
                    } else {
                        RegistroRasd.Dettaglio dettaglio = registro.dettaglio(voce.id());
                        c.dettagli++;
                        p.setDiscipline(dettaglio.discipline())
                                .setTesseratiAttivi(dettaglio.tesseratiAttivi())
                                .setAttivitaSportiveUltimoAnno(dettaglio.attivitaSportiveUltimoAnno())
                                .setPartecipazioniUltimoAnno(dettaglio.partecipazioniUltimoAnno())
                                .setDettaglioLetto(adesso())
                                .setCalcio(calcio(voce.organismi(), dettaglio.discipline()));
                    }
                }
                daSalvare.add(p);
            }
        } finally {
            // Anche se il registro blocca a metà pagina: i dettagli già letti non si richiedono più.
            c.lette += daSalvare.size();
            prospect.saveAll(daSalvare);
        }
        return completa;
    }

    /** Affiliata FIGC, o con una disciplina che comincia per «Calcio» (calcio a 5, a 11…). */
    static boolean calcio(List<String> organismi, List<String> discipline) {
        if (organismi != null && organismi.stream().anyMatch("FIGC"::equalsIgnoreCase)) {
            return true;
        }
        return discipline != null
                && discipline.stream().anyMatch(d -> d.toLowerCase().startsWith("calcio"));
    }
}
