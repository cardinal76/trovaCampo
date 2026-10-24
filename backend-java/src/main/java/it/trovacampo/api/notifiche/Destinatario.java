package it.trovacampo.api.notifiche;

import java.util.List;

/**
 * Chi riceve gli avvisi, per quello che serve a sceglierli: le squadre che
 * segue, la posizione e il raggio, e quali dei due avvisi vuole.
 *
 * <p>Lo è un'{@link Iscrizione} per il {@link PianificatoreNotifiche}, e lo
 * sono le scelte che il browser manda alla pagina Prossime partite
 * ({@link ProssimePartite}): così tutti e due scelgono le partite con la
 * stessa regola, in {@link PartiteDaAvvisare}.
 */
public interface Destinatario {

    /** Le chiavi delle squadre seguite: vedi {@link ChiaveSquadra}. */
    List<String> squadre();

    boolean avvisoSquadre();

    boolean avvisoVicino();

    Double lat();

    Double lng();

    Integer raggioKm();

    default boolean haPosizione() {
        return lat() != null && lng() != null && raggioKm() != null;
    }
}
