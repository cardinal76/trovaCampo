package it.trovacampo.api.prospect;

import it.trovacampo.api.dominio.Testo;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Il nome di una società ridotto alle parole che la distinguono, per
 * confrontare la denominazione legale del registro («ASSOCIAZIONE SPORTIVA
 * DILETTANTISTICA VIS SUBIACO») con il nome corto dei comunicati LND («A.S.D.
 * VIS SUBIACO»): via accenti, punteggiatura, forma giuridica e anno di
 * fondazione; le sigle puntate (A.S.D., S.S.D.R.L.) si riattaccano prima.
 */
final class NomiSocieta {

    /** Forme giuridiche e parole che non distinguono una società dall'altra. */
    private static final Set<String> GENERICHE =
            Set.of(
                    "asd", "ssd", "srl", "arl", "ssdarl", "ssdrl", "ssdsrl", "ads", "apd", "asc",
                    "usd", "acd", "acsd", "ass", "assoc", "associaz", "soc", "pol", "polisportiva",
                    "calcio", "associazione", "sportiva", "societa", "dilettantistica",
                    "dilettantistico", "dilett", "dil", "a", "r", "l", "rl", "responsabilita",
                    "limitata", "onlus", "aps", "ets", "di", "del", "della", "dei", "e", "la", "il",
                    "lo", "de", "in", "sigla", "breve", "denominata", "anche", "forma", "abbreviata",
                    "promozione", "sociale", "c5", "a5", "c11", "5", "11", "s", "d");

    private static final Pattern NON_ALFANUMERICI = Pattern.compile("[^a-z0-9]+");
    private static final Pattern ANNO = Pattern.compile("(19|20)\\d\\d");

    private NomiSocieta() {}

    /** Le parole distintive, nell'ordine in cui compaiono. */
    static List<String> parole(String nome) {
        String[] grezze = NON_ALFANUMERICI.split(Testo.normalizza(nome));
        List<String> unite = new ArrayList<>();
        StringBuilder sigla = new StringBuilder();
        for (String parola : grezze) {
            if (parola.isEmpty()) {
                continue;
            }
            // "a s d" -> "asd": le lettere sole di fila sono una sigla puntata.
            if (parola.length() == 1 && Character.isLetter(parola.charAt(0))) {
                sigla.append(parola);
                continue;
            }
            if (!sigla.isEmpty()) {
                unite.add(sigla.toString());
                sigla.setLength(0);
            }
            unite.add(parola);
        }
        if (!sigla.isEmpty()) {
            unite.add(sigla.toString());
        }
        return unite.stream()
                .filter(p -> !GENERICHE.contains(p) && !ANNO.matcher(p).matches())
                .toList();
    }

    static String chiave(String nome) {
        return String.join(" ", parole(nome));
    }
}
