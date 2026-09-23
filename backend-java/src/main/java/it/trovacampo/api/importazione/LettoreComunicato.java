package it.trovacampo.api.importazione;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

/**
 * Legge il "programma gare" dal PDF di un Comunicato Ufficiale LND e ne
 * ricava un campo per ogni squadra di casa.
 *
 * <p>Il PDF è un tabulato a caratteri di larghezza fissa, e ogni partita
 * occupa due righe:
 *
 * <pre>
 *  1)  BOREALE                          LUISS S.S.D.A R.L.               190  DON ORIONE        (SINTEX)        ROMA (CAMILLUCCIA)               6/09/26  11:00
 *                                                                             VIA DELLA CAMILLUCCIA 120
 * </pre>
 *
 * Squadra di casa, ospite, codice e nome dell'impianto, comune (con la
 * frazione fra parentesi), data e ora; sotto, l'indirizzo. Le colonne si
 * leggono per posizione, perché i nomi stessi contengono spazi e non c'è un
 * separatore affidabile. Una riga di partita che non rispetta il tracciato
 * (niente data al suo posto) finisce fra gli scarti invece di produrre un
 * campo con i pezzi sbagliati.
 *
 * <p>Il campo è quello della squadra di casa: è lei che gioca lì. La stessa
 * squadra compare in più campionati sullo stesso campo (prima squadra,
 * juniores): il doppione si tiene una volta sola, senza segnalarlo.
 */
class LettoreComunicato {

    private static final Pattern PARTITA = Pattern.compile("^\\s*\\d+\\)\\s");
    private static final Pattern DATA_ORA =
            Pattern.compile("^\\s*\\d{1,2}/\\d{1,2}/\\d{2}\\s+\\d{1,2}[:.]\\d{2}");
    /** Il fondo scritto in coda al nome dell'impianto: "(SINTEX)", "ERBA"... */
    private static final Pattern FONDO =
            Pattern.compile("\\(?\\b(SINTEX|SINTETICO|ERBA|TERRA|XXXXXX)\\b\\)?\\s*$");

    // Le colonne del tabulato, contate dall'inizio della riga.
    private static final int CASA = 5;
    private static final int OSPITE = 38;
    private static final int CODICE = 68;
    private static final int IMPIANTO = 76;
    private static final int LOCALITA = 110;
    private static final int DATA = 143;

    /** Il codice che il comitato usa per i campi non ancora assegnati. */
    private static final String DA_DEFINIRE = "9999";

    LettoreExcel.Lettura leggi(InputStream file) {
        String testo;
        try (PDDocument documento = Loader.loadPDF(file.readAllBytes())) {
            PDFTextStripper estrattore = new PDFTextStripper();
            // Le righe nell'ordine in cui si vedono, non in quello in cui
            // sono state scritte nel file.
            estrattore.setSortByPosition(true);
            testo = estrattore.getText(documento);
        } catch (IOException eccezione) {
            throw new LettoreExcel.FileNonValidoException("Il PDF non si riesce a leggere");
        }
        return leggiTesto(testo);
    }

    /** Separato dalla lettura del PDF per poterlo provare su righe scritte a mano. */
    LettoreExcel.Lettura leggiTesto(String testo) {
        String[] righe = testo.split("\\R");
        List<RigaExcel> campi = new ArrayList<>();
        List<Scarto> scarti = new ArrayList<>();
        Set<String> giaVisti = new HashSet<>();
        int partite = 0;

        for (int i = 0; i < righe.length; i++) {
            String riga = righe[i];
            if (!PARTITA.matcher(riga).find()) {
                continue;
            }
            partite++;
            String casa = colonna(riga, CASA, OSPITE);

            if (riga.length() <= DATA || !DATA_ORA.matcher(riga.substring(DATA)).find()) {
                scarti.add(new Scarto(partite, casa + ": riga non nel formato del programma gare"));
                continue;
            }
            if (DA_DEFINIRE.equals(colonna(riga, CODICE, IMPIANTO))) {
                scarti.add(new Scarto(partite, casa + ": campo da definire"));
                continue;
            }

            String impianto = colonna(riga, IMPIANTO, LOCALITA);
            Matcher fondo = FONDO.matcher(impianto);
            if (fondo.find()) {
                impianto = impianto.substring(0, fondo.start()).strip();
            }
            String comune = comune(colonna(riga, LOCALITA, DATA));
            String indirizzo =
                    i + 1 < righe.length ? righe[i + 1].strip().replaceAll("\\s+", " ") : "";

            if (casa.isEmpty() || impianto.isEmpty() || indirizzo.isEmpty()) {
                scarti.add(new Scarto(partite, casa + ": mancano impianto o indirizzo"));
                continue;
            }
            if (giaVisti.add(ImportazioneService.chiave(casa, impianto))) {
                campi.add(new RigaExcel(partite, casa, impianto, indirizzo, comune, "", null, null));
            }
        }

        if (partite == 0) {
            throw new LettoreExcel.FileNonValidoException(
                    "Nel PDF non c'è un programma gare: nessuna partita trovata");
        }
        return new LettoreExcel.Lettura(campi, scarti, List.of());
    }

    /**
     * "ROMA (CAMILLUCCIA)" diventa "ROMA": per la geocodifica conta il comune,
     * la frazione fra parentesi la confonde. La colonna è a larghezza fissa e
     * a volte la parentesi arriva tagliata ("GUIDONIA MONTECELIO (VILLALBA")
     * o attaccata con un trattino ("GUIDONIA MONTECELIO-(VILLANOVA").
     */
    static String comune(String localita) {
        int parentesi = localita.indexOf('(');
        String comune = parentesi < 0 ? localita : localita.substring(0, parentesi);
        return comune.replaceAll("[\\s-]+$", "").strip();
    }

    private static String colonna(String riga, int inizio, int fine) {
        if (riga.length() <= inizio) {
            return "";
        }
        return riga.substring(inizio, Math.min(fine, riga.length())).strip();
    }
}
