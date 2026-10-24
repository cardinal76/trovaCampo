package it.trovacampo.api.notifiche;

import java.util.Collection;
import java.util.Set;

/**
 * Le notifiche già mandate, perché nessuna parta due volte: né al giro dopo
 * del pianificatore, che rivede la stessa partita nella sua finestra, né dopo
 * un riavvio del backend.
 */
public interface RegistroInvii {

    /**
     * Prenota l'invio {@code chiave}: vero la prima volta, falso se è già stato
     * prenotato. Si prenota prima di mandare, così due giri sovrapposti non
     * mandano la stessa notifica.
     */
    boolean prenota(String chiave);

    /** Toglie la prenotazione di un invio non riuscito: il giro dopo riprova. */
    void annulla(String chiave);

    /**
     * Quali di questi invii sono già stati prenotati (cioè mandati, o in
     * corso): per la pagina Prossime partite, che scrive «avviso inviato».
     * Si legge e basta, senza prenotare niente.
     */
    Set<String> giaPrenotati(Collection<String> chiavi);
}
