package it.trovacampo.api.indirizzi;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Cerca l'indirizzo del campo per tutte le società di calcio del Lazio nella
 * collection {@code prospect} e scrive l'esito in {@code indirizzo_prospect}.
 *
 * <p>Parte a mano, dall'amministrazione, e gira in sottofondo su un thread
 * solo: con la rete accesa ogni società senza campo LND né impianto open data
 * costa fino a tre richieste a Nominatim, una al secondo, e qualche migliaio
 * di società sono un'ora o due. Una corsa alla volta.
 *
 * <p>Se {@code prospect} non esiste ancora (la riempie l'importazione del
 * Registro RASD) l'avvio lo dice e non fa niente.
 */
@Service
public class ArricchimentoProspect {

    private static final Logger log = LoggerFactory.getLogger(ArricchimentoProspect.class);
    static final double OBIETTIVO = 0.70;

    private final LettoreProspect lettore;
    private final LocalizzatoreSocieta localizzatore;
    private final IndirizziProspectRepository risultati;
    private final Executor esecutore;
    private final Clock orologio;
    private final AtomicBoolean inCorso = new AtomicBoolean();
    private volatile Stato stato = Stato.MAI_PARTITO;

    @Autowired
    public ArricchimentoProspect(
            LettoreProspect lettore, LocalizzatoreSocieta localizzatore, IndirizziProspectRepository risultati) {
        this(lettore, localizzatore, risultati,
                Executors.newSingleThreadExecutor(
                        lavoro -> {
                            Thread thread = new Thread(lavoro, "arricchimento-prospect");
                            thread.setDaemon(true);
                            return thread;
                        }),
                Clock.systemUTC());
    }

    ArricchimentoProspect(
            LettoreProspect lettore,
            LocalizzatoreSocieta localizzatore,
            IndirizziProspectRepository risultati,
            Executor esecutore,
            Clock orologio) {
        this.lettore = lettore;
        this.localizzatore = localizzatore;
        this.risultati = risultati;
        this.esecutore = esecutore;
        this.orologio = orologio;
    }

    /**
     * @param usaRete geocodifica degli indirizzi senza posizione e ricerca su OSM
     * @param ritenta rifà anche le società già cercate (trovate o no); senza,
     *     si cercano solo quelle mai cercate e quelle cercate senza rete, se
     *     ora la rete c'è
     * @param limite al massimo quante società cercare in questa corsa; nullo per tutte
     */
    public record Richiesta(boolean usaRete, boolean ritenta, Integer limite) {}

    public record Stato(
            boolean inCorso,
            Instant iniziataIl,
            Instant finitaIl,
            int daCercare,
            int cercate,
            int trovate,
            int errori,
            boolean conRete,
            String messaggio) {
        static final Stato MAI_PARTITO =
                new Stato(false, null, null, 0, 0, 0, 0, false, "Nessun arricchimento lanciato dall'avvio del backend");
    }

    public record Avvio(boolean avviato, String messaggio, Stato stato) {}

    public Stato stato() {
        return stato;
    }

    public Avvio avvia(Richiesta richiesta) {
        if (!lettore.esiste()) {
            return new Avvio(false, messaggioSenzaProspect(), stato);
        }
        if (!inCorso.compareAndSet(false, true)) {
            return new Avvio(false, "Un arricchimento è già in corso", stato);
        }
        stato = new Stato(true, orologio.instant(), null, 0, 0, 0, 0, richiesta.usaRete(), "In preparazione");
        try {
            esecutore.execute(() -> esegui(richiesta));
        } catch (RuntimeException eccezione) {
            inCorso.set(false);
            throw eccezione;
        }
        return new Avvio(true, "Arricchimento avviato", stato);
    }

    private String messaggioSenzaProspect() {
        return "La collection " + lettore.collection()
                + " non esiste ancora: va prima importato il Registro RASD. Niente da arricchire.";
    }

    void esegui(Richiesta richiesta) {
        Instant inizio = stato.iniziataIl();
        int cercate = 0;
        int trovate = 0;
        int errori = 0;
        int daCercare = 0;
        try {
            List<LettoreProspect.SocietaProspect> societa = lettore.societaDiCalcioDelLazio().societa();
            Map<String, IndirizzoProspect> precedenti =
                    risultati.findAllById(societa.stream().map(LettoreProspect.SocietaProspect::codiceFiscale).toList())
                            .stream()
                            .collect(Collectors.toMap(IndirizzoProspect::codiceFiscale, Function.identity()));
            List<LettoreProspect.SocietaProspect> coda =
                    societa.stream()
                            .filter(una -> richiesta.ritenta() || daCercare(precedenti.get(una.codiceFiscale()), richiesta))
                            .limit(richiesta.limite() == null ? Long.MAX_VALUE : Math.max(0, richiesta.limite()))
                            .toList();
            daCercare = coda.size();
            LocalizzatoreSocieta.Opzioni opzioni = new LocalizzatoreSocieta.Opzioni(richiesta.usaRete());

            for (LettoreProspect.SocietaProspect una : coda) {
                try {
                    IndirizzoMigliore trovato = localizzatore.trova(una.daLocalizzare(), opzioni).orElse(null);
                    risultati.save(IndirizzoProspect.di(una, trovato, richiesta.usaRete(), orologio.instant()));
                    if (trovato != null) {
                        trovate++;
                    }
                } catch (RuntimeException eccezione) {
                    errori++;
                    log.warn("Indirizzo di {} non cercato: {}", una.codiceFiscale(), eccezione.toString());
                }
                cercate++;
                stato = new Stato(true, inizio, null, daCercare, cercate, trovate, errori, richiesta.usaRete(), "In corso");
            }
            stato = new Stato(false, inizio, orologio.instant(), daCercare, cercate, trovate, errori,
                    richiesta.usaRete(), "Finito");
            log.info("Arricchimento indirizzi: {} cercate, {} trovate, {} errori", cercate, trovate, errori);
        } catch (RuntimeException eccezione) {
            log.error("Arricchimento indirizzi interrotto", eccezione);
            stato = new Stato(false, inizio, orologio.instant(), daCercare, cercate, trovate, errori,
                    richiesta.usaRete(), "Interrotto: " + eccezione.getMessage());
        } finally {
            inCorso.set(false);
        }
    }

    private static boolean daCercare(IndirizzoProspect precedente, Richiesta richiesta) {
        if (precedente == null) {
            return true;
        }
        // Trovata senza rete ma senza posizione: con la rete si può geocodificare.
        boolean senzaPosizione = precedente.trovato() && precedente.lat() == null;
        return !precedente.conRete() && richiesta.usaRete() && (!precedente.trovato() || senzaPosizione);
    }

    /**
     * Quante società di calcio del Lazio hanno un indirizzo, divise per fonte.
     *
     * @param disponibile false se {@code prospect} non esiste ancora
     * @param totale le società di calcio del Lazio in {@code prospect}
     * @param cercate quante hanno già un esito in {@code indirizzo_prospect}
     * @param percentuale con indirizzo / totale
     */
    public record Copertura(
            boolean disponibile,
            String messaggio,
            int totale,
            int cercate,
            int conIndirizzo,
            int conCoordinate,
            double percentuale,
            double obiettivo,
            Map<Fonte, Integer> perFonte,
            Map<Confidenza, Integer> perConfidenza,
            boolean filtroCalcio,
            String criterioLazio) {}

    public Copertura copertura() {
        if (!lettore.esiste()) {
            return new Copertura(false, messaggioSenzaProspect(), 0, 0, 0, 0, 0, OBIETTIVO,
                    Map.of(), Map.of(), false, null);
        }
        LettoreProspect.Lettura lettura = lettore.societaDiCalcioDelLazio();
        List<IndirizzoProspect> esiti =
                risultati.findAllById(
                        lettura.societa().stream().map(LettoreProspect.SocietaProspect::codiceFiscale).toList());

        Map<Fonte, Integer> perFonte = new EnumMap<>(Fonte.class);
        Map<Confidenza, Integer> perConfidenza = new EnumMap<>(Confidenza.class);
        int conIndirizzo = 0;
        int conCoordinate = 0;
        for (IndirizzoProspect esito : esiti) {
            if (!esito.trovato()) {
                continue;
            }
            conIndirizzo++;
            if (esito.lat() != null) {
                conCoordinate++;
            }
            perFonte.merge(esito.fonte(), 1, Integer::sum);
            perConfidenza.merge(esito.confidenza(), 1, Integer::sum);
        }
        int totale = lettura.societa().size();
        String messaggio =
                lettura.filtroCalcio()
                        ? "Società di calcio del Lazio (" + lettura.criterioLazio() + ")"
                        : "I documenti di " + lettore.collection()
                                + " non dicono gli organismi affilianti: contate tutte le società del Lazio ("
                                + lettura.criterioLazio() + ")";
        return new Copertura(
                true, messaggio, totale, esiti.size(), conIndirizzo, conCoordinate,
                totale == 0 ? 0 : (double) conIndirizzo / totale, OBIETTIVO, perFonte, perConfidenza,
                lettura.filtroCalcio(), lettura.criterioLazio());
    }
}
