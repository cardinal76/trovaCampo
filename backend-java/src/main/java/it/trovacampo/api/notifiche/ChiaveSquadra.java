package it.trovacampo.api.notifiche;

import it.trovacampo.api.dominio.Testo;
import java.util.Collection;
import java.util.regex.Pattern;

/**
 * Come si riconosce una squadra seguita, nelle squadre della scheda e nelle
 * partite del calendario.
 *
 * <p>La chiave è società di presenze + campionato + ente + lettera della
 * squadra ("" per la prima, "B" per la seconda), per esempio
 * {@code 412|eccellenza|regionali|}. Non è l'id della squadra nel girone
 * ({@code squadra_iscritta} di presenze) di proposito: quello cambia a ogni
 * stagione e a ogni fase, e chi segue "la Juniores della Lodigiani" smetterebbe
 * di ricevere gli avvisi a settembre senza saperlo. Nome del campionato ed
 * ente invece si ripetono da una stagione all'altra, e l'id della società in
 * presenze resta lo stesso. Cambia solo con una promozione o una
 * retrocessione, quando la squadra è davvero in un altro campionato.
 *
 * <p>La lettera serve a distinguere due squadre della stessa società nello
 * stesso campionato (la seconda di solito fuori classifica). Un presenze che
 * nelle partite non la manda ancora dà {@code null}: allora vale qualunque
 * squadra di quella società in quel campionato, che è meglio di nessun
 * avviso.
 */
public final class ChiaveSquadra {

    /** Il separatore non compare nei testi normalizzati: {@link #pulisci} lo toglie. */
    private static final String SEPARATORE = "|";

    /** La forma di una chiave che arriva dal telefono: si accetta solo questa. */
    public static final Pattern FORMA = Pattern.compile("\\d{1,12}\\|[a-z0-9 ]{0,120}\\|[a-z0-9 ]{0,120}\\|[a-z0-9 ]{0,10}");

    private ChiaveSquadra() {}

    /** Null se manca la società: una squadra che non si sa di chi è non si può seguire. */
    public static String di(Long societaPresenzeId, String campionato, String ente, String squadra) {
        if (societaPresenzeId == null) {
            return null;
        }
        return prefisso(societaPresenzeId, campionato, ente) + pulisci(squadra);
    }

    /**
     * Se fra le chiavi seguite c'è la squadra di quella società in quel
     * campionato. Con {@code squadra} null (presenze non l'ha detta) basta la
     * società nel campionato.
     */
    public static boolean seguita(
            Collection<String> seguite, Long societaPresenzeId, String campionato, String ente, String squadra) {
        if (societaPresenzeId == null || seguite == null || seguite.isEmpty()) {
            return false;
        }
        if (squadra != null) {
            return seguite.contains(di(societaPresenzeId, campionato, ente, squadra));
        }
        String prefisso = prefisso(societaPresenzeId, campionato, ente);
        return seguite.stream().anyMatch(chiave -> chiave.startsWith(prefisso));
    }

    private static String prefisso(long societaPresenzeId, String campionato, String ente) {
        return societaPresenzeId + SEPARATORE + pulisci(campionato) + SEPARATORE + pulisci(ente) + SEPARATORE;
    }

    /**
     * Minuscolo, senza accenti e senza punteggiatura: "Juniores Under 19 -
     * Regionale" e "JUNIORES UNDER 19 REGIONALE" sono lo stesso campionato,
     * scritto da due comunicati diversi.
     */
    static String pulisci(String testo) {
        return Testo.normalizza(testo).replaceAll("[^a-z0-9]+", " ").trim();
    }
}
