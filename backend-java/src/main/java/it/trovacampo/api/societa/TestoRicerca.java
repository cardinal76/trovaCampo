package it.trovacampo.api.societa;

import java.util.stream.Stream;

/** La copia normalizzata dei campi di una società su cui lavora la ricerca. */
public final class TestoRicerca {

    private TestoRicerca() {}

    /** Ricalcola il testo di ricerca della società e la restituisce. */
    public static Societa aggiorna(Societa societa) {
        String testo =
                Stream.of(
                                societa.getNomeSocieta(),
                                societa.getNomeImpianto(),
                                societa.getIndirizzoImpianto(),
                                societa.getLocalitaImpianto())
                        .map(Testo::normalizza)
                        .filter(parte -> !parte.isEmpty())
                        .reduce((a, b) -> a + " | " + b)
                        .orElse("");

        return societa.setTestoRicerca(testo);
    }
}
