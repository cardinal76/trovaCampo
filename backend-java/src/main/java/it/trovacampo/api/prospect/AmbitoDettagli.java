package it.trovacampo.api.prospect;

import java.util.Locale;

/**
 * Per quali società si chiede il dettaglio (discipline, tesserati): una
 * richiesta in più per ognuna, quindi si sceglie a ogni lancio. L'ambito
 * geografico lo danno le regioni dell'importazione.
 */
public enum AmbitoDettagli {
    /** Solo l'elenco. */
    NESSUNO(0),
    /** Le affiliate FIGC: circa il 12% del registro. */
    FIGC(0.12),
    /** Il calcio: FIGC, o una disciplina «Calcio…» già letta. */
    CALCIO(0.13),
    /** Tutte: una richiesta per società, oltre centomila in Italia. */
    TUTTE(1.0);

    /** Che parte delle società del registro tocca, per la stima dei tempi. */
    final double quota;

    AmbitoDettagli(double quota) {
        this.quota = quota;
    }

    boolean vuole(RegistroRasd.Voce voce, Prospect p) {
        return switch (this) {
            case NESSUNO -> false;
            case FIGC -> voce.organismi() != null && voce.organismi().stream().anyMatch("FIGC"::equalsIgnoreCase);
            case CALCIO -> p.isCalcio();
            case TUTTE -> true;
        };
    }

    /** «figc», «calcio», «tutte», «nessuno»; «true»/«false» come prima (calcio / niente). */
    public static AmbitoDettagli da(String testo) {
        if (testo == null || testo.isBlank()) {
            return CALCIO;
        }
        return switch (testo.trim().toLowerCase(Locale.ROOT)) {
            case "true", "si", "sì" -> CALCIO;
            case "false", "no" -> NESSUNO;
            case "tutti" -> TUTTE;
            default -> valueOf(testo.trim().toUpperCase(Locale.ROOT));
        };
    }
}
