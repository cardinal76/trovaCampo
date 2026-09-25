package it.trovacampo.api.dominio;

import java.time.Instant;
import java.util.Objects;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un campo arrivato dall'anagrafica di presenze che chi amministra ha
 * eliminato: la sincronizzazione non lo ricrea più.
 *
 * <p>Senza questo promemoria un doppione tolto a mano (per esempio un campo
 * "DA DESIGNARE" letto male da un comunicato) tornerebbe al giro di
 * sincronizzazione successivo, qualche ora dopo. Si abbina alle righe con le
 * stesse chiavi con cui la sincronizzazione ritrova le società già presenti:
 * nome della società più nome del campo ({@link #chiave(String, String)}),
 * oppure la coppia di id di presenze, che resta la stessa anche se il nome
 * del campo cambia grafia da un comunicato all'altro.
 *
 * <p>Nomi e indirizzo servono solo a chi legge l'elenco, per riconoscere cosa
 * ha escluso; chi e quando, a ricostruire la storia.
 *
 * @param chiave nome della società e del campo compattati, vedi {@link #chiave(String, String)}
 * @param anagraficaSocietaId la società in presenze, se la scheda ce l'aveva
 * @param anagraficaImpiantoId il campo in presenze, se la scheda ce l'aveva
 */
@Document(collection = "societa_escluse")
public record Esclusione(
        @Id String id,
        String chiave,
        Long anagraficaSocietaId,
        Long anagraficaImpiantoId,
        String nomeSocieta,
        String nomeImpianto,
        String indirizzoImpianto,
        String esclusaDa,
        Instant esclusaIl) {

    /** L'esclusione di una scheda che sta per essere eliminata. */
    public static Esclusione di(Societa societa, String chi, Instant quando) {
        return new Esclusione(
                null,
                chiave(societa.getNomeSocieta(), societa.getNomeImpianto()),
                societa.getAnagraficaSocietaId(),
                societa.getAnagraficaImpiantoId(),
                societa.getNomeSocieta(),
                societa.getNomeImpianto(),
                societa.getIndirizzoImpianto(),
                chi,
                quando);
    }

    /**
     * Vero se la riga con questa chiave e questi id di presenze è il campo
     * escluso. Gli id valgono solo in coppia: la stessa società di presenze
     * può avere un campo giusto e uno sbagliato (è il caso di POMEZIA CALCIO
     * 1957), e togliere quello sbagliato non deve portarsi via l'altro.
     */
    public boolean riguarda(String chiaveRiga, Long societaId, Long impiantoId) {
        if (chiave != null && chiave.equals(chiaveRiga)) {
            return true;
        }
        return anagraficaSocietaId != null
                && anagraficaImpiantoId != null
                && Objects.equals(anagraficaSocietaId, societaId)
                && Objects.equals(anagraficaImpiantoId, impiantoId);
    }

    /**
     * "A.S.D. Certosa  Calcio" e "asd certosa calcio" sono la stessa società:
     * la chiave con cui importazione e sincronizzazione riconoscono una scheda
     * già presente.
     */
    public static String chiave(String nomeSocieta, String nomeImpianto) {
        return compatta(nomeSocieta) + "|" + compatta(nomeImpianto);
    }

    private static String compatta(String testo) {
        return Testo.normalizza(testo).replaceAll("[^a-z0-9]+", "");
    }
}
