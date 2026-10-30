package it.trovacampo.api.indirizzi;

import it.trovacampo.api.dominio.Testo;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * La chiave con cui si confrontano i comuni di fonti diverse.
 *
 * <p>Il registro scrive "Roma (RM)", i comunicati "ROMA" o "OSTIA", il
 * censimento "Civitavecchia", OpenStreetMap "Roma Capitale" o "Lido di
 * Ostia": tutti devono finire sulla stessa chiave, altrimenti il confronto
 * dei nomi, che si fa solo dentro lo stesso comune, non parte nemmeno.
 */
public final class Comuni {

    private static final Pattern PARENTESI = Pattern.compile("\\(.*\\)");
    private static final Pattern NON_ALFANUMERICI = Pattern.compile("[^a-z0-9]+");

    /**
     * Frazioni e quartieri di Roma che i comunicati e OSM scrivono al posto
     * del comune. Non serve che siano tutti: una frazione che manca fa solo
     * perdere un abbinamento, non ne crea uno sbagliato.
     */
    private static final Set<String> FRAZIONI_DI_ROMA =
            Set.of(
                    "ostia", "ostia lido", "lido di ostia", "ostia antica", "acilia", "casal palocco",
                    "infernetto", "dragona", "dragoncello", "axa", "malafede", "vitinia", "fregene",
                    "castel fusano", "torre angela", "tor bella monaca", "ponte di nona", "lunghezza",
                    "settecamini", "cesano", "la storta", "osteria nuova", "ottavia", "spinaceto",
                    "trigoria", "castel di leva", "santa palomba", "torrenova", "borghesiana",
                    "finocchio", "tor vergata", "roma capitale");

    private Comuni() {}

    /** "Roma (RM)", "ROMA", "Lido di Ostia" diventano "roma"; "Sant'Oreste" diventa "sant oreste". */
    public static String chiave(String comune) {
        if (comune == null) {
            return "";
        }
        String testo = PARENTESI.matcher(Testo.normalizza(comune)).replaceAll(" ");
        testo = NON_ALFANUMERICI.matcher(testo).replaceAll(" ").strip();
        if (FRAZIONI_DI_ROMA.contains(testo)) {
            return "roma";
        }
        return testo;
    }

    public static boolean stesso(String uno, String altro) {
        String chiave = chiave(uno);
        return !chiave.isEmpty() && chiave.equals(chiave(altro));
    }
}
