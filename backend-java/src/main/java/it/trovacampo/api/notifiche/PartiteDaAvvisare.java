package it.trovacampo.api.notifiche;

import it.trovacampo.api.anagrafica.AnagraficaPresenze.Partita;
import it.trovacampo.api.anagrafica.PartiteSuiCampi;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Quali partite fanno partire un avviso, e per chi: la regola sola, senza
 * l'orario e senza l'invio.
 *
 * <p>La usano il {@link PianificatoreNotifiche}, che dalle partite delle
 * prossime ore tiene quelle con l'avviso da mandare adesso, e la pagina
 * Prossime partite ({@link ProssimePartite}), che le mostra tutte per la
 * settimana. Stessa regola in un posto solo: la pagina non può promettere un
 * avviso che il pianificatore non manda, né tacerne uno che manda.
 *
 * <ul>
 *   <li>{@link TipoAvviso#SQUADRA}: una partita di una squadra seguita, in
 *       casa o fuori, per chi ha acceso l'avviso delle squadre;
 *   <li>{@link TipoAvviso#VICINO}: una partita su un campo con le coordinate
 *       entro il raggio dalla posizione salvata, per chi ha acceso l'avviso
 *       delle vicine.
 * </ul>
 *
 * <p>Le partite vengono dalla stessa cache di scheda e mappa
 * ({@link PartiteSuiCampi#fra}): nessuna chiamata in più a presenze.
 */
@Component
public class PartiteDaAvvisare {

    private static final double RAGGIO_TERRA_KM = 6371.0;

    /**
     * Un avviso da mandare (o già mandato) a un destinatario.
     *
     * @param campo il campo di TrovaCampo dove si gioca; null se non c'è
     * @param distanzaKm dalla posizione del destinatario, solo per {@link TipoAvviso#VICINO}
     */
    public record Avviso<D extends Destinatario>(
            D destinatario, Partita partita, TipoAvviso tipo, Societa campo, Double distanzaKm) {

        public Instant inizio() {
            return partita.dataOra().toInstant();
        }
    }

    private final PartiteSuiCampi partite;
    private final SocietaRepository campi;

    public PartiteDaAvvisare(PartiteSuiCampi partite, SocietaRepository campi) {
        this.partite = partite;
        this.campi = campi;
    }

    /**
     * Gli avvisi delle partite che cominciano fra {@code da} (compreso) e
     * {@code a} (escluso), per data; per ogni partita prima quelli delle
     * squadre seguite, poi quelli delle vicine.
     */
    public <D extends Destinatario> List<Avviso<D>> fra(Instant da, Instant a, Collection<D> destinatari) {
        if (destinatari.isEmpty()) {
            return List.of();
        }
        List<Partita> inProgramma = partite.fra(da, a);
        if (inProgramma.isEmpty()) {
            return List.of();
        }
        Map<Long, List<Societa>> campiPerImpianto =
                campi.findByAnagraficaImpiantoIdIn(
                                inProgramma.stream().map(Partita::impiantoId).collect(Collectors.toSet()))
                        .stream()
                        .collect(Collectors.groupingBy(Societa::getAnagraficaImpiantoId));

        List<Avviso<D>> avvisi = new ArrayList<>();
        for (Partita partita : inProgramma) {
            Societa campo = campoDi(partita, campiPerImpianto.getOrDefault(partita.impiantoId(), List.of()));
            for (D destinatario : destinatari) {
                if (destinatario.avvisoSquadre() && !seguite(destinatario, partita).isEmpty()) {
                    avvisi.add(new Avviso<>(destinatario, partita, TipoAvviso.SQUADRA, campo, null));
                }
            }
            if (campo == null || campo.getLat() == null || campo.getLng() == null) {
                continue;
            }
            for (D destinatario : destinatari) {
                if (!destinatario.avvisoVicino() || !destinatario.haPosizione()) {
                    continue;
                }
                double distanza =
                        distanzaKm(destinatario.lat(), destinatario.lng(), campo.getLat(), campo.getLng());
                if (distanza <= destinatario.raggioKm()) {
                    avvisi.add(new Avviso<>(destinatario, partita, TipoAvviso.VICINO, campo, distanza));
                }
            }
        }
        return avvisi;
    }

    /**
     * Le squadre di questa partita che il destinatario segue, con il nome
     * scritto nella partita: di solito una, due se segue tutte e due.
     */
    public static List<String> seguite(Destinatario destinatario, Partita partita) {
        List<String> seguite = new ArrayList<>(2);
        if (ChiaveSquadra.seguita(
                destinatario.squadre(), partita.casaSocietaId(), partita.campionato(), partita.ente(),
                partita.casaSquadra())) {
            seguite.add(partita.casa());
        }
        if (ChiaveSquadra.seguita(
                destinatario.squadre(), partita.ospiteSocietaId(), partita.campionato(), partita.ente(),
                partita.ospiteSquadra())) {
            seguite.add(partita.ospite());
        }
        return seguite;
    }

    /**
     * Come il {@link RegistroInvii} ricorda un avviso mandato a
     * un'iscrizione. Con la data dentro: una partita spostata ad altra ora si
     * riannuncia.
     */
    public static String chiaveInvio(String iscrizioneId, TipoAvviso tipo, Partita partita) {
        return iscrizioneId + "|" + tipo + "|" + identificativo(partita) + "|"
                + partita.dataOra().toInstant().getEpochSecond();
    }

    static String identificativo(Partita partita) {
        return partita.id() != null
                ? String.valueOf(partita.id())
                : partita.impiantoId() + "-" + partita.casa() + "-" + partita.ospite();
    }

    /**
     * Il campo di TrovaCampo dove si gioca: fra le società che giocano su
     * quell'impianto, quella di casa se c'è (la notifica apre la sua scheda),
     * altrimenti la prima con le coordinate.
     */
    private static Societa campoDi(Partita partita, List<Societa> sullImpianto) {
        return sullImpianto.stream()
                .min(Comparator.comparing(
                                (Societa societa) -> !Objects.equals(
                                        societa.getAnagraficaSocietaId(), partita.casaSocietaId()))
                        .thenComparing(societa -> societa.getLat() == null)
                        .thenComparing(Societa::getId, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElse(null);
    }

    /** Haversine: sulle decine di chilometri l'errore è di metri. */
    static double distanzaKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2)
                        + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * RAGGIO_TERRA_KM * Math.asin(Math.sqrt(a));
    }
}
