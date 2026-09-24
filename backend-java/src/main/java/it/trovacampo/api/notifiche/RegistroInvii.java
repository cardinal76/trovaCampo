package it.trovacampo.api.notifiche;

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
}
