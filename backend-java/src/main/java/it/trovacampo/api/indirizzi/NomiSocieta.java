package it.trovacampo.api.indirizzi;

import it.trovacampo.api.dominio.Testo;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Il confronto fra i nomi di una società come li scrivono fonti diverse.
 *
 * <p>Il Registro RASD scrive "A.S.D. POLISPORTIVA DE ROSSI", i Comunicati LND
 * "POL. DE ROSSI", il censimento degli impianti "Centro Sportivo De Rossi":
 * prima di confrontarli si tolgono forme giuridiche (ASD, SSD, S.S.D. a r.l.,
 * A.P.D.), parole che non distinguono una società dall'altra ("Polisportiva",
 * "Calcio", "Football Club"), punteggiatura, accenti e anni di fondazione
 * scritti da una parte sola. Quello che resta sono le parole che contano, e
 * su quelle si misura la somiglianza.
 *
 * <p>La somiglianza da sola non basta: "ATLETICO SAN LORENZO" e "SAN LORENZO"
 * possono essere due società in due paesi diversi. Per questo chi la usa
 * confronta solo nomi dello stesso comune ({@link Comuni}).
 */
public final class NomiSocieta {

    /** Sotto questa somiglianza due nomi dello stesso comune non sono la stessa società. */
    public static final double SOGLIA = 0.8;

    /** "a.s.d.", "s.s.d", "f.c.": iniziali puntate, da unire in una parola sola. */
    private static final Pattern SIGLA_PUNTATA =
            Pattern.compile("\\b(?:[a-z0-9]\\.){2,}(?:[a-z0-9]\\b)?");
    private static final Pattern NON_ALFANUMERICI = Pattern.compile("[^a-z0-9]+");
    private static final Pattern ANNO = Pattern.compile("(18|19|20)\\d\\d");

    /** Forme giuridiche scritte per esteso: si tolgono prima di dividere in parole. */
    private static final List<Pattern> FRASI_GIURIDICHE =
            List.of(
                    Pattern.compile("\\ba ?r ?l\\b"),
                    Pattern.compile("\\ba responsabilita limitata\\b"),
                    Pattern.compile("\\bassociazione sportiva dilettantistica\\b"),
                    Pattern.compile("\\bsocieta sportiva dilettantistica\\b"),
                    Pattern.compile("\\bsocieta cooperativa\\b"));

    /** Forme giuridiche e sigle societarie, in qualsiasi grafia una volta tolti i punti. */
    private static final Set<String> FORME_GIURIDICHE =
            Set.of(
                    "asd", "ssd", "ssdarl", "ssdrl", "asdrl", "arl", "rl", "srl", "sr", "srls",
                    "asdpol", "apd", "aps", "asdc", "asc", "usd", "gsd", "ssc", "ets", "onlus",
                    "coop", "scarl", "as", "ss", "ac", "us", "gs", "sc", "cs", "ad", "ud", "pd", "sd",
                    "pol", "polisp", "polisportiva", "ass", "associazione", "societa", "sportiva",
                    "sportivo", "dilettantistica", "dilettantistico", "dilettanti", "unione",
                    "circolo", "gruppo",
                    // Le abbreviazioni con cui il registro tronca la denominazione legale.
                    "dilett", "dilettant", "dil", "assoc", "associaz", "soc", "ads", "acd", "acsd",
                    "ssdsrl", "promozione", "sociale",
                    // "... in forma abbreviata", "denominata anche ...", "sigla ...".
                    "sigla", "breve", "denominata", "anche", "forma", "abbreviata");

    /** Parole che quasi ogni società di calcio ha nel nome: non distinguono. */
    private static final Set<String> PAROLE_GENERICHE =
            Set.of("calcio", "football", "club", "fc", "cf", "afc", "sport", "c5", "a5", "c11", "11");

    /** Articoli e preposizioni: "Città di Ostia" e "Città Ostia" sono lo stesso nome. */
    private static final Set<String> PAROLE_VUOTE =
            Set.of(
                    "di", "da", "del", "della", "dello", "dei", "degli", "delle", "la", "il", "lo",
                    "le", "gli", "e", "ed", "in");

    /** Le parole che nel nome di un impianto dicono che è un campo, non chi ci gioca. */
    private static final Set<String> PAROLE_IMPIANTO =
            Set.of(
                    "campo", "campi", "sportivo", "sportiva", "sportivi", "centro", "stadio",
                    "comunale", "comunali", "impianto", "impianti", "polivalente", "palazzetto",
                    "palestra", "complesso", "parrocchiale", "parrocchia", "oratorio", "struttura",
                    "polisportivo");

    private static final Map<String, String> ABBREVIAZIONI =
            Map.of("s", "san", "sta", "santa", "sto", "santo");

    private NomiSocieta() {}

    /**
     * Le parole che distinguono una società: "A.S.D. Polisportiva Città
     * di Ciampino 1945" diventa [citta, ciampino]. Gli anni restano fuori:
     * si guardano a parte, in {@link #somiglianza}.
     */
    public static List<String> parole(String nome) {
        return parole(nome, Set.of());
    }

    /** Come {@link #parole(String)}, per il nome di un impianto: "Stadio Comunale Galli" diventa [galli]. */
    public static List<String> paroleImpianto(String nome) {
        return parole(nome, PAROLE_IMPIANTO);
    }

    /** Le parole senza le forme giuridiche, gli anni e le parole generiche. */
    static List<String> parole(String nome, Set<String> altreDaTogliere) {
        List<String> tutte = paroleGrezze(nome);
        List<String> significative = new ArrayList<>();
        for (int i = 0; i < tutte.size(); i++) {
            String parola = tutte.get(i);
            // "S. LORENZO" è San Lorenzo, ma solo davanti a un'altra parola.
            // "SS." resta una forma giuridica: "SS. PIETRO E PAOLO" (i santi) e
            // "S.S. LAZIO" non si distinguono, e toglierlo da entrambe le parti
            // non cambia il confronto.
            if (ABBREVIAZIONI.containsKey(parola) && i + 1 < tutte.size()) {
                parola = ABBREVIAZIONI.get(parola);
            }
            if (!daTogliere(parola, altreDaTogliere) && !ANNO.matcher(parola).matches()) {
                significative.add(parola);
            }
        }
        if (significative.isEmpty() && altreDaTogliere.isEmpty()) {
            // "A.S.D. CALCIO 2000": meglio le parole generiche che nessuna parola.
            // Non per un impianto: "Campo Sportivo Comunale" non dice di chi è.
            return tutte.stream()
                    .filter(parola -> !FORME_GIURIDICHE.contains(parola) && !PAROLE_VUOTE.contains(parola))
                    .toList();
        }
        return significative;
    }

    /**
     * Le parole distintive unite da uno spazio: la chiave con cui i campi LND
     * si confrontano per nome esatto ("A.S.D. VIS SUBIACO" e "ASS.SPORTIVA
     * DILETTANT.VIS SUBIACO" hanno la stessa chiave, "vis subiaco").
     */
    public static String chiave(String nome) {
        return String.join(" ", parole(nome));
    }

    /**
     * Il comune ridotto come un nome ({@link Comuni#chiave} più {@link #chiave}),
     * per confrontarlo con la chiave di una società che porta il nome del paese:
     * "S.S.D. COLONNA" con sede a "Colonna (RM)".
     */
    public static String chiaveComune(String comune) {
        return chiave(Comuni.chiave(comune));
    }

    /** Gli anni scritti nel nome: "LODIGIANI CALCIO 1972" ha [1972]. */
    static List<String> anni(String nome) {
        return paroleGrezze(nome).stream().filter(parola -> ANNO.matcher(parola).matches()).toList();
    }

    private static boolean daTogliere(String parola, Set<String> altreDaTogliere) {
        return parola.length() < 2
                || FORME_GIURIDICHE.contains(parola)
                || PAROLE_GENERICHE.contains(parola)
                || PAROLE_VUOTE.contains(parola)
                || altreDaTogliere.contains(parola);
    }

    /** Minuscolo, senza accenti, senza i punti delle sigle e senza le frasi giuridiche. */
    private static List<String> paroleGrezze(String nome) {
        String testo = Testo.normalizza(nome).replace('\'', ' ').replace('’', ' ');
        // "a.s.d." -> "asd", "s.s.d. a r.l." -> "ssd a rl": le sigle puntate si uniscono.
        testo =
                SIGLA_PUNTATA.matcher(testo)
                        .replaceAll(sigla -> sigla.group().replaceAll("[.\\s]", "") + " ");
        testo = NON_ALFANUMERICI.matcher(testo).replaceAll(" ");
        for (Pattern frase : FRASI_GIURIDICHE) {
            testo = frase.matcher(testo).replaceAll(" ");
        }
        return Arrays.stream(testo.strip().split("\\s+")).filter(parola -> !parola.isEmpty()).toList();
    }

    /** La somiglianza fra due nomi di società, da 0 a 1. */
    public static double somiglianza(String uno, String altro) {
        return somiglianza(parole(uno), anni(uno), parole(altro), anni(altro));
    }

    /**
     * La somiglianza fra le parole già estratte di due nomi: la quota di parole
     * in comune (coefficiente di Dice), dove due parole lunghe che differiscono
     * per un refuso contano come uguali. Due nomi che, tolti gli spazi, sono la
     * stessa stringa ("TORBELLAMONACA" e "TOR BELLA MONACA") valgono 1; un nome
     * di almeno due parole identiche tutto contenuto nell'altro ("CASAL BERNOCCHI" in
     * "CVN CASAL BERNOCCHI") vale almeno 0,85. Due anni di fondazione diversi
     * tolgono un quarto: "ATLETICO 2000" e "ATLETICO 1999" restano sotto la soglia.
     */
    public static double somiglianza(
            List<String> parole, Collection<String> anni, List<String> altre, Collection<String> altriAnni) {
        if (parole.isEmpty() || altre.isEmpty()) {
            return 0;
        }
        double punteggio;
        if (String.join("", parole).equals(String.join("", altre))) {
            punteggio = 1;
        } else {
            int comuni = paroleInComune(parole, altre);
            punteggio = 2.0 * comuni / (parole.size() + altre.size());
            List<String> corto = parole.size() <= altre.size() ? parole : altre;
            List<String> lungo = corto == parole ? altre : parole;
            // Contenuto vuol dire parole identiche: con il refuso tollerato
            // "SAN MARINO" starebbe dentro "MARIO PAOLUCCI SAN CLETO".
            if (corto.size() >= 2 && lungo.containsAll(corto)) {
                punteggio = Math.max(punteggio, 0.85);
            }
        }
        if (!anni.isEmpty() && !altriAnni.isEmpty() && anni.stream().noneMatch(altriAnni::contains)) {
            punteggio *= 0.75;
        }
        return punteggio;
    }

    private static int paroleInComune(List<String> parole, List<String> altre) {
        List<String> disponibili = new ArrayList<>(altre);
        int comuni = 0;
        for (String parola : parole) {
            for (int i = 0; i < disponibili.size(); i++) {
                if (uguali(parola, disponibili.get(i))) {
                    disponibili.remove(i);
                    comuni++;
                    break;
                }
            }
        }
        return comuni;
    }

    /** Uguali, o lunghe e diverse per un refuso ("BORGHESIANA" e "BORGHESANA"). */
    static boolean uguali(String una, String altra) {
        if (una.equals(altra)) {
            return true;
        }
        int corta = Math.min(una.length(), altra.length());
        if (corta < 5) {
            return false;
        }
        return distanza(una, altra) <= (corta >= 9 ? 2 : 1);
    }

    /** Distanza di Levenshtein. */
    static int distanza(String a, String b) {
        int[] riga = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            riga[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            int diagonale = riga[0];
            riga[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int sopra = riga[j];
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                riga[j] = Math.min(Math.min(riga[j] + 1, riga[j - 1] + 1), diagonale + costo);
                diagonale = sopra;
            }
        }
        return riga[b.length()];
    }
}
