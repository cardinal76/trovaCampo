package it.trovacampo.api.indirizzi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Gli impianti con il calcio di un dataset comunale di dati.veneto.it.
 *
 * <p>Due modelli, letti per nome di colonna con {@link LettoreCsv.Colonne}:
 *
 * <ul>
 *   <li>quello dei comuni dell'Alto Vicentino (Schio, Thiene, Malo...):
 *       {@code Impianto_Denominazione}, {@code Impianto_Via},
 *       {@code Impianto_Civico}, {@code Impianto_Comune},
 *       {@code Impianto_Tipologia}, {@code Impianto_Disciplina},
 *       {@code Latitudine}/{@code Longitudine};
 *   <li>quello di Verona: {@code DENOMINAZIONE EDIFICIO}, {@code INDIRIZZO},
 *       {@code TIPOLOGIA ATTIVITA' SPORTIVA}, {@code CIRCOSCRIZIONE},
 *       {@code LATITUDINE}/{@code LONGITUDINE}, senza il comune.
 * </ul>
 *
 * <p>Il modello vicentino ha anche {@code Impianto_Contatto} e
 * {@code Impianto_Contatto_Email}, spesso di una persona o di una società:
 * <b>non si leggono</b>, come il resto delle colonne non elencate qui.
 */
final class ImpiantiVeneto {

    static final String[] NOME = {"Impianto_Denominazione", "DENOMINAZIONE EDIFICIO", "Denominazione"};
    static final String[] VIA = {"Impianto_Via", "INDIRIZZO", "Via"};
    static final String[] CIVICO = {"Impianto_Civico", "Civico"};
    static final String[] COMUNE = {"Impianto_Comune", "Comune"};
    static final String[] TIPOLOGIA = {"Impianto_Tipologia", "Tipologia"};
    static final String[] DISCIPLINA = {"Impianto_Disciplina", "TIPOLOGIA ATTIVITA' SPORTIVA", "Disciplina"};
    static final String[] CIRCOSCRIZIONE = {"CIRCOSCRIZIONE", "Quartiere"};
    static final String[] LATITUDINE = {"Latitudine", "LAT"};
    static final String[] LONGITUDINE = {"Longitudine", "LON", "LNG"};

    private static final Pattern CALCIO = Pattern.compile("calci|calcett|futsal");
    private static final Pattern A_CINQUE = Pattern.compile("calcetto|futsal|calcio\\s*(a\\s*)?5\\b|c5\\b");
    private static final Pattern A_SETTE = Pattern.compile("calcio\\s*(a\\s*)?[789]\\b");
    private static final Pattern A_UNDICI = Pattern.compile("calcio\\s*(a\\s*)?11\\b");
    private static final Pattern CAMPO_GRANDE = Pattern.compile("campo|stadio");
    private static final Pattern NON_CAMPO_GRANDE = Pattern.compile("palestra|area verde|palazzetto|calcetto|oratorio");
    /** Un trattino o un apostrofo persi nella codifica del file: "Impianto Vallorcola ? Calcio". */
    private static final Pattern CARATTERE_PERSO = Pattern.compile("\\s\\?\\s");

    private ImpiantiVeneto() {}

    static List<ImpiantoOpenData> impianti(
            List<List<String>> righe, OpenDataVeneto.Dataset dataset, Instant adesso) {
        if (righe.isEmpty()) {
            return List.of();
        }
        LettoreCsv.Colonne colonne = new LettoreCsv.Colonne(righe.get(0));
        if (!colonne.ha(NOME) || !colonne.ha(VIA)) {
            throw new IllegalStateException(
                    "Il dataset " + dataset.titolo() + " non ha le colonne del nome o dell'indirizzo:"
                            + " è cambiato il formato?");
        }

        Map<String, ImpiantoOpenData> impianti = new LinkedHashMap<>();
        for (List<String> riga : righe.subList(1, righe.size())) {
            String nome = CARATTERE_PERSO.matcher(colonne.valore(riga, NOME)).replaceAll(" - ").strip();
            String indirizzo = indirizzo(colonne.valore(riga, VIA), colonne.valore(riga, CIVICO));
            String comune = colonne.valore(riga, COMUNE);
            if (comune.isEmpty()) {
                comune = dataset.comune() == null ? "" : dataset.comune();
            }
            if (nome.isEmpty() || comune.isEmpty()) {
                continue;
            }
            List<String> sport = sport(colonne.valore(riga, TIPOLOGIA), colonne.valore(riga, DISCIPLINA), nome);
            if (sport.isEmpty()) {
                continue;
            }
            double[] posizione =
                    posizione(colonne.valore(riga, LATITUDINE), colonne.valore(riga, LONGITUDINE));
            String circoscrizione = colonne.valore(riga, CIRCOSCRIZIONE);
            String id = ImportatoreImpiantiOpenData.id(FonteOpenData.VENETO, nome, comune, indirizzo);
            ImpiantoOpenData gia = impianti.get(id);
            if (gia != null) {
                // "Campo da calcio" e "Campo da calcio a 5" sulla stessa via hanno lo
                // stesso id (il numero non è una parola distintiva): un impianto solo,
                // con le discipline di tutti e due e la prima posizione che c'è.
                Set<String> tutte = new LinkedHashSet<>(gia.sport());
                tutte.addAll(sport);
                sport = List.copyOf(tutte);
                if (gia.lat() != null) {
                    posizione = new double[] {gia.lat(), gia.lng()};
                }
                nome = gia.nome();
            }
            impianti.put(
                    id,
                    new ImpiantoOpenData(
                            id, FonteOpenData.VENETO.sigla(), FonteOpenData.VENETO.licenza(), nome, comune,
                            circoscrizione.isEmpty() ? null : circoscrizione, indirizzo, sport,
                            posizione == null ? null : posizione[0], posizione == null ? null : posizione[1],
                            adesso, dataset.attribuzione()));
        }
        return List.copyOf(impianti.values());
    }

    /** "Via Grumetto" e "104"; il civico non si ripete se la via lo porta già ("via Martiri della Libertà 10"). */
    static String indirizzo(String via, String civico) {
        if (civico.isEmpty() || via.endsWith(" " + civico) || via.endsWith("," + civico)) {
            return via;
        }
        return via.isEmpty() ? civico : via + " " + civico;
    }

    /**
     * Le discipline di calcio, con i nomi del censimento di Roma: Calcio-11,
     * Calcio-5, Calcio-7, o Calcio quando il dataset non dice di più.
     *
     * <p>Un «calcio» senza numero conta come campo a 11 solo se l'impianto è
     * un campo o uno stadio ("campo calcio", "Stadio") e non una palestra,
     * un'area verde o un campo da calcetto: è quello che serve alla regola del
     * campo del paese di {@link FonteImpiantiOpenData}.
     */
    static List<String> sport(String tipologia, String disciplina, String nome) {
        String tipo = tipologia.toLowerCase(Locale.ITALIAN);
        String nomeMinuscolo = nome.toLowerCase(Locale.ITALIAN);
        Set<String> sport = new LinkedHashSet<>();
        List<String> voci = new ArrayList<>(List.of(disciplina.toLowerCase(Locale.ITALIAN).split("[,;/]")));
        if (voci.stream().noneMatch(voce -> CALCIO.matcher(voce).find())) {
            // Thiene: "Campo da Calcio" nella tipologia, disciplina vuota.
            voci = List.of(tipo, nomeMinuscolo);
        }
        for (String voce : voci) {
            if (!CALCIO.matcher(voce).find()) {
                continue;
            }
            if (A_UNDICI.matcher(voce).find()) {
                sport.add("Calcio-11");
            } else if (A_CINQUE.matcher(voce).find() || A_CINQUE.matcher(nomeMinuscolo).find()) {
                sport.add("Calcio-5");
            } else if (A_SETTE.matcher(voce).find()) {
                sport.add("Calcio-7");
            } else if (CAMPO_GRANDE.matcher(tipo + " " + nomeMinuscolo).find()
                    && !NON_CAMPO_GRANDE.matcher(tipo + " " + nomeMinuscolo).find()) {
                sport.add("Calcio-11");
            } else {
                sport.add("Calcio");
            }
        }
        return List.copyOf(sport);
    }

    /**
     * Latitudine e longitudine, con la virgola o il punto, solo se cadono in
     * Italia; scambiate se il file le ha invertite. Nulla altrimenti: le dà
     * la geocodifica dell'indirizzo.
     */
    static double[] posizione(String latitudine, String longitudine) {
        Double lat = numero(latitudine);
        Double lng = numero(longitudine);
        if (lat == null || lng == null) {
            return null;
        }
        if (inItalia(lat, lng)) {
            return new double[] {lat, lng};
        }
        if (inItalia(lng, lat)) {
            return new double[] {lng, lat};
        }
        return null;
    }

    private static boolean inItalia(double lat, double lng) {
        return lat >= 35.4 && lat <= 47.2 && lng >= 6.5 && lng <= 18.6;
    }

    private static Double numero(String testo) {
        if (testo == null || testo.isBlank()) {
            return null;
        }
        try {
            return Double.valueOf(testo.strip().replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
