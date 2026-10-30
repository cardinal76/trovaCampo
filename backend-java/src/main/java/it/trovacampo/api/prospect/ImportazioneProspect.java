package it.trovacampo.api.prospect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * L'importazione del Registro nazionale (RASD) nella collection
 * {@code prospect}, lanciata a mano dall'amministrazione: niente giri
 * programmati.
 *
 * <ul>
 *   <li>legge l'elenco una regione alla volta, a pagine di {@link #PAGINA}
 *       righe, con almeno un secondo fra una richiesta e l'altra
 *       ({@link RegistroRasd});
 *   <li>salva o aggiorna per codice fiscale: rileggere una pagina non crea
 *       doppioni, e i dati già arricchiti (dettaglio, campo) restano;
 *   <li>dopo ogni pagina salva il prossimo {@code start} della regione
 *       ({@link StatoImportazioneProspect}): fermata o interrotta, riparte da
 *       lì. Con {@code ricomincia} si riparte da zero;
 *   <li>chiede il dettaglio (discipline, tesserati) solo per il calcio del
 *       Lazio, una volta sola per società: per tutta Italia sarebbero
 *       centomila chiamate, più di un giorno a una al secondo;
 *   <li>a Lazio finito, collega i prospect ai campi ({@link CollegamentoCampi}).
 * </ul>
 *
 * <p>Gira in un thread suo, uno solo per volta: un'importazione nazionale
 * dura qualche ora, una richiesta HTTP no.
 */
@Service
public class ImportazioneProspect {

    private static final Logger log = LoggerFactory.getLogger(ImportazioneProspect.class);

    static final int PAGINA = 100;
    static final int LAZIO = 12;

    private final RegistroRasd registro;
    private final ProspectRepository prospect;
    private final StatiImportazioneRepository stati;
    private final CollegamentoCampi collegamento;
    private final ExecutorService esecutore;

    private final AtomicBoolean inCorso = new AtomicBoolean(false);
    private volatile boolean fermati = false;
    private volatile Avanzamento ultimo = Avanzamento.mai();

    @Autowired
    public ImportazioneProspect(
            RegistroRasd registro,
            ProspectRepository prospect,
            StatiImportazioneRepository stati,
            CollegamentoCampi collegamento) {
        this(
                registro,
                prospect,
                stati,
                collegamento,
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
            CollegamentoCampi collegamento,
            ExecutorService esecutore) {
        this.registro = registro;
        this.prospect = prospect;
        this.stati = stati;
        this.collegamento = collegamento;
        this.esecutore = esecutore;
    }

    /**
     * Cosa importare: le regioni (vuoto = tutte, Lazio per primo), quante
     * pagine al massimo in questo giro (nullo = fino in fondo; utile per una
     * prova), se ripartire da zero e se leggere il dettaglio del calcio laziale.
     */
    public record Opzioni(List<Integer> regioni, Integer maxPagine, boolean ricomincia, boolean dettagli) {

        public static Opzioni tutto() {
            return new Opzioni(List.of(), null, false, true);
        }

        List<Integer> regioniDaFare() {
            return regioni == null || regioni.isEmpty()
                    ? List.copyOf(RegistroRasd.REGIONI.keySet())
                    : regioni;
        }
    }

    /** Come è andato (o come sta andando) l'ultimo giro. */
    public record Avanzamento(
            String stato,
            Instant iniziato,
            Instant finito,
            int pagine,
            int richieste,
            int lette,
            int nuove,
            int dettagli,
            String regioneCorrente,
            String errore,
            CollegamentoCampi.Esito collegamento) {

        static Avanzamento mai() {
            return new Avanzamento("mai", null, null, 0, 0, 0, 0, 0, null, null, null);
        }
    }

    /** Avanzamento dell'ultimo giro e punto di ripresa di ogni regione. */
    public record Stato(boolean inCorso, Avanzamento ultimo, List<StatoImportazioneProspect> regioni) {}

    public Stato stato() {
        return new Stato(inCorso.get(), ultimo, stati.findAll());
    }

    /** Parte in sottofondo. Falso se ce n'è già una in corso. */
    public boolean avvia(Opzioni opzioni, String chi) {
        if (!inCorso.compareAndSet(false, true)) {
            return false;
        }
        fermati = false;
        ultimo = new Avanzamento("in corso", Instant.now(), null, 0, 0, 0, 0, 0, null, null, null);
        log.info("Importazione del registro RASD avviata da {}: {}", chi, opzioni);
        esecutore.submit(
                () -> {
                    try {
                        esegui(opzioni);
                    } finally {
                        inCorso.set(false);
                    }
                });
        return true;
    }

    /** Chiede di fermarsi alla fine della richiesta in corso: si riprende da lì. */
    public void ferma() {
        fermati = true;
    }

    /**
     * Il giro vero, nel thread di chi chiama: lo usa {@link #avvia} e lo
     * usano i test.
     */
    Avanzamento esegui(Opzioni opzioni) {
        Contatori c = new Contatori();
        Instant iniziato = Instant.now();
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
            esito = "bloccata";
            errore = e.getMessage();
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
        if (lazioToccato) {
            try {
                collegati = collegamento.collega();
            } catch (RuntimeException e) {
                log.error("Collegamento dei prospect ai campi fallito", e);
            }
        }
        Avanzamento fatto =
                new Avanzamento(
                        esito, iniziato, Instant.now(), c.pagine, c.richieste, c.lette, c.nuove,
                        c.dettagli, regioneCorrente, errore, collegati);
        ultimo = fatto;
        log.info(
                "Importazione del registro RASD {}: {} pagine, {} società lette ({} nuove), {} dettagli",
                esito, c.pagine, c.lette, c.nuove, c.dettagli);
        return fatto;
    }

    private static final class Contatori {
        int pagine;
        int richieste;
        int lette;
        int nuove;
        int dettagli;
    }

    /** Vero se la regione è arrivata in fondo, falso se ci si è fermati prima. */
    private boolean importaRegione(StatoImportazioneProspect stato, Opzioni opzioni, Contatori c)
            throws InterruptedException {
        while (true) {
            if (fermati || (opzioni.maxPagine() != null && c.pagine >= opzioni.maxPagine())) {
                return false;
            }
            int start = stato.getProssimoStart();
            RegistroRasd.Pagina pagina = registro.pagina(stato.getRegioneId(), start, PAGINA);
            c.richieste++;
            c.pagine++;
            boolean completa =
                    aggiorna(c, pagina.voci(), opzioni.dettagli() && stato.getRegioneId() == LAZIO);
            if (!completa) {
                // Fermati a metà dei dettagli: la pagina si rilegge al prossimo giro.
                return false;
            }

            int prossimo = start + pagina.voci().size();
            boolean finita = pagina.voci().isEmpty() || prossimo >= pagina.totale();
            stato.setProssimoStart(finita ? 0 : prossimo)
                    .setTotale(pagina.totale())
                    .setCompletata(finita)
                    .setAggiornato(Instant.now());
            stati.save(stato);
            ultimo =
                    new Avanzamento(
                            "in corso", ultimo.iniziato(), null, c.pagine, c.richieste, c.lette,
                            c.nuove, c.dettagli, stato.getRegione(), null, null);
            if (finita) {
                return true;
            }
        }
    }

    /** Falso se ci si è fermati prima di leggere tutti i dettagli della pagina. */
    private boolean aggiorna(Contatori c, List<RegistroRasd.Voce> voci, boolean conDettagli)
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
                    .setAggiornato(Instant.now());
            if (conDettagli && p.isCalcio() && p.getDettaglioLetto() == null) {
                if (fermati) {
                    completa = false;
                    daSalvare.add(p);
                    continue;
                }
                RegistroRasd.Dettaglio dettaglio = registro.dettaglio(voce.id());
                c.richieste++;
                c.dettagli++;
                p.setDiscipline(dettaglio.discipline())
                        .setTesseratiAttivi(dettaglio.tesseratiAttivi())
                        .setAttivitaSportiveUltimoAnno(dettaglio.attivitaSportiveUltimoAnno())
                        .setPartecipazioniUltimoAnno(dettaglio.partecipazioniUltimoAnno())
                        .setDettaglioLetto(Instant.now())
                        .setCalcio(calcio(voce.organismi(), dettaglio.discipline()));
            }
            daSalvare.add(p);
        }
        c.lette += voci.size();
        prospect.saveAll(daSalvare);
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
