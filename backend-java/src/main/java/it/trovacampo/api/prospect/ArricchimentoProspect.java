package it.trovacampo.api.prospect;

import it.trovacampo.api.indirizzi.IndirizzoMigliore;
import it.trovacampo.api.indirizzi.LocalizzatoreSocieta;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * La ricerca dell'indirizzo con la rete, per i prospect di calcio del Lazio
 * che il {@link CollegamentoCampi} ha lasciato senza indirizzo o senza
 * coordinate: geocodifica dell'indirizzo open data, poi il campo su
 * OpenStreetMap.
 *
 * <p>Fino a tre richieste a Nominatim per società, una al secondo per tutto
 * il backend ({@link it.trovacampo.api.service.RitmoNominatim}): qualche
 * centinaio di società sono una ventina di minuti. Per questo parte a mano,
 * in sottofondo su un thread solo, una corsa alla volta. Una società già
 * cercata così non si ricerca, salvo {@code ritenta}.
 */
@Service
public class ArricchimentoProspect {

    private static final Logger log = LoggerFactory.getLogger(ArricchimentoProspect.class);

    static final String NESSUN_PROSPECT =
            "Nessun prospect di calcio del Lazio: va prima importato il Registro RASD (Importa il Lazio).";

    private final ProspectRepository prospect;
    private final LocalizzatoreSocieta localizzatore;
    private final Executor esecutore;
    private final Clock orologio;
    private final AtomicBoolean inCorso = new AtomicBoolean();
    private volatile Stato stato = Stato.MAI_PARTITO;

    @Autowired
    public ArricchimentoProspect(ProspectRepository prospect, LocalizzatoreSocieta localizzatore) {
        this(prospect, localizzatore,
                Executors.newSingleThreadExecutor(
                        lavoro -> {
                            Thread thread = new Thread(lavoro, "arricchimento-prospect");
                            thread.setDaemon(true);
                            return thread;
                        }),
                Clock.systemUTC());
    }

    ArricchimentoProspect(
            ProspectRepository prospect, LocalizzatoreSocieta localizzatore, Executor esecutore, Clock orologio) {
        this.prospect = prospect;
        this.localizzatore = localizzatore;
        this.esecutore = esecutore;
        this.orologio = orologio;
    }

    /**
     * @param ritenta cerca anche le società già cercate con la rete
     * @param limite al massimo quante società in questa corsa; nullo per tutte
     */
    public record Richiesta(boolean ritenta, Integer limite) {}

    public record Stato(
            boolean inCorso,
            Instant iniziataIl,
            Instant finitaIl,
            int daCercare,
            int cercate,
            int trovate,
            int errori,
            String messaggio) {
        static final Stato MAI_PARTITO =
                new Stato(false, null, null, 0, 0, 0, 0, "Nessuna ricerca lanciata dall'avvio del backend");
    }

    public record Avvio(boolean avviato, String messaggio, Stato stato) {}

    public Stato stato() {
        return stato;
    }

    public Avvio avvia(Richiesta richiesta) {
        if (!inCorso.compareAndSet(false, true)) {
            return new Avvio(false, "Una ricerca è già in corso", stato);
        }
        List<Prospect> coda;
        try {
            List<Prospect> lazio = prospect.findByRegioneAndCalcioTrue(CollegamentoCampi.LAZIO);
            if (lazio.isEmpty()) {
                inCorso.set(false);
                return new Avvio(false, NESSUN_PROSPECT, stato);
            }
            coda =
                    lazio.stream()
                            .filter(p -> p.getFonteIndirizzo() == null || p.getLat() == null)
                            .filter(p -> richiesta.ritenta() || p.getGeocodificaTentata() == null)
                            .limit(richiesta.limite() == null ? Long.MAX_VALUE : Math.max(0, richiesta.limite()))
                            .toList();
            stato = new Stato(true, orologio.instant(), null, coda.size(), 0, 0, 0, "In corso");
            esecutore.execute(() -> esegui(coda));
        } catch (RuntimeException eccezione) {
            inCorso.set(false);
            throw eccezione;
        }
        return new Avvio(true, "Ricerca avviata su " + coda.size() + " società", stato);
    }

    private void esegui(List<Prospect> coda) {
        Instant inizio = stato.iniziataIl();
        int cercate = 0;
        int trovate = 0;
        int errori = 0;
        try {
            for (Prospect p : coda) {
                try {
                    Instant adesso = orologio.instant();
                    Optional<IndirizzoMigliore> trovato =
                            localizzatore.trova(
                                    CollegamentoCampi.daLocalizzare(p), LocalizzatoreSocieta.Opzioni.COMPLETA);
                    if (trovato.isPresent() && CollegamentoCampi.applica(p, trovato.get(), adesso)
                            && p.getLat() != null) {
                        trovate++;
                    }
                    prospect.save(p.setGeocodificaTentata(adesso));
                } catch (RuntimeException eccezione) {
                    errori++;
                    log.warn("Indirizzo di {} non cercato: {}", p.getCodiceFiscale(), eccezione.toString());
                }
                cercate++;
                stato = new Stato(true, inizio, null, coda.size(), cercate, trovate, errori, "In corso");
            }
            stato = new Stato(false, inizio, orologio.instant(), coda.size(), cercate, trovate, errori, "Finita");
            log.info("Ricerca indirizzi con la rete: {} cercate, {} con la posizione, {} errori",
                    cercate, trovate, errori);
        } catch (RuntimeException eccezione) {
            log.error("Ricerca indirizzi interrotta", eccezione);
            stato = new Stato(false, inizio, orologio.instant(), coda.size(), cercate, trovate, errori,
                    "Interrotta: " + eccezione.getMessage());
        } finally {
            inCorso.set(false);
        }
    }
}
