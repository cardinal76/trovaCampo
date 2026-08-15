package it.trovacampo.api.dominio;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normalizzazione del testo usata dalla ricerca. */
public final class Testo {

    private static final Pattern DIACRITICI = Pattern.compile("\\p{M}+");
    private static final Pattern METACARATTERI_REGEX = Pattern.compile("[\\\\.\\[\\]{}()*+?^$|/]");

    private Testo() {}

    /** Minuscolo e senza accenti, così "Città" e "citta" si equivalgono. */
    public static String normalizza(String testo) {
        if (testo == null) {
            return "";
        }
        String senzaAccenti =
                DIACRITICI.matcher(Normalizer.normalize(testo, Normalizer.Form.NFD)).replaceAll("");
        return senzaAccenti.toLowerCase(Locale.ITALIAN).trim();
    }

    /**
     * Neutralizza i metacaratteri prima di usare il termine cercato dentro
     * una regex di MongoDB: senza questo una ricerca per "S.r.l." o "(" si
     * comporterebbe in modo imprevisto o farebbe fallire la query.
     */
    public static String perRegex(String testo) {
        return METACARATTERI_REGEX.matcher(testo).replaceAll("\\\\$0");
    }
}
