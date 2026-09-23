package it.trovacampo.api.importazione;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Legge il primo foglio di un file Excel (.xlsx o .xls) e ne ricava le righe da
 * importare.
 *
 * <p>L'intestazione non deve per forza stare sulla prima riga: spesso sopra ci
 * sono un titolo o una data. Vale la prima, fra le prime {@value
 * #RIGHE_CERCATE_PER_INTESTAZIONE}, che contiene la colonna della società.
 */
class LettoreExcel {

    private static final int RIGHE_CERCATE_PER_INTESTAZIONE = 10;

    record Lettura(List<RigaExcel> righe, List<Scarto> scarti, List<String> colonneIgnorate) {}

    /** Il file non si può importare per niente: non è Excel o mancano colonne. */
    static class FileNonValidoException extends RuntimeException {
        FileNonValidoException(String messaggio) {
            super(messaggio);
        }
    }

    Lettura leggi(InputStream file) {
        Workbook cartella;
        try {
            cartella = WorkbookFactory.create(file);
        } catch (IOException | RuntimeException eccezione) {
            // POI lancia eccezioni diverse per un CSV, un PDF o un xlsx
            // troncato: per chi carica il file sono tutte la stessa cosa.
            throw new FileNonValidoException("Il file non è un Excel leggibile (.xlsx o .xls)");
        }

        try (cartella) {
            if (cartella.getNumberOfSheets() == 0) {
                throw new FileNonValidoException("Il file non contiene fogli");
            }
            return leggiFoglio(cartella.getSheetAt(0), cartella);
        } catch (IOException eccezione) {
            throw new FileNonValidoException("Il file non è un Excel leggibile (.xlsx o .xls)");
        }
    }

    private Lettura leggiFoglio(Sheet foglio, Workbook cartella) {
        DataFormatter formattatore = new DataFormatter();
        FormulaEvaluator valutatore = cartella.getCreationHelper().createFormulaEvaluator();

        Row intestazione = trovaIntestazione(foglio, formattatore, valutatore);
        Map<Colonna, Integer> posizioni = new EnumMap<>(Colonna.class);
        List<String> ignorate = new ArrayList<>();

        for (Cell cella : intestazione) {
            String nome = formattatore.formatCellValue(cella, valutatore).trim();
            if (nome.isEmpty()) {
                continue;
            }
            Colonna.perIntestazione(nome)
                    .ifPresentOrElse(
                            colonna -> posizioni.putIfAbsent(colonna, cella.getColumnIndex()),
                            () -> ignorate.add(nome));
        }

        List<String> mancanti =
                Arrays.stream(Colonna.values())
                        .filter(Colonna::obbligatoria)
                        .filter(colonna -> !posizioni.containsKey(colonna))
                        .map(Colonna::nomePrincipale)
                        .toList();
        if (!mancanti.isEmpty()) {
            throw new FileNonValidoException(
                    "Mancano le colonne obbligatorie: " + String.join(", ", mancanti));
        }

        List<RigaExcel> righe = new ArrayList<>();
        List<Scarto> scarti = new ArrayList<>();

        for (int indice = intestazione.getRowNum() + 1; indice <= foglio.getLastRowNum(); indice++) {
            Row riga = foglio.getRow(indice);
            // Il numero che vede chi apre il file: Excel conta da 1.
            int numero = indice + 1;
            Map<Colonna, String> valori = new EnumMap<>(Colonna.class);
            posizioni.forEach(
                    (colonna, posizione) ->
                            valori.put(colonna, testo(riga, posizione, formattatore, valutatore)));

            if (valori.values().stream().allMatch(String::isEmpty)) {
                continue;
            }

            try {
                righe.add(converti(numero, valori));
            } catch (IllegalArgumentException motivo) {
                scarti.add(new Scarto(numero, motivo.getMessage()));
            }
        }

        return new Lettura(righe, scarti, ignorate);
    }

    private Row trovaIntestazione(Sheet foglio, DataFormatter formattatore, FormulaEvaluator valutatore) {
        int ultima = Math.min(foglio.getLastRowNum(), RIGHE_CERCATE_PER_INTESTAZIONE - 1);
        for (int indice = foglio.getFirstRowNum(); indice <= ultima; indice++) {
            Row riga = foglio.getRow(indice);
            if (riga == null) {
                continue;
            }
            for (Cell cella : riga) {
                String nome = formattatore.formatCellValue(cella, valutatore);
                if (Colonna.perIntestazione(nome).filter(c -> c == Colonna.NOME_SOCIETA).isPresent()) {
                    return riga;
                }
            }
        }
        throw new FileNonValidoException(
                "Intestazione non trovata: nelle prime "
                        + RIGHE_CERCATE_PER_INTESTAZIONE
                        + " righe non c'è una colonna 'Società'");
    }

    private RigaExcel converti(int numero, Map<Colonna, String> valori) {
        List<String> vuote =
                valori.entrySet().stream()
                        .filter(voce -> voce.getKey().obbligatoria() && voce.getValue().isEmpty())
                        .map(voce -> voce.getKey().nomePrincipale())
                        .collect(Collectors.toList());
        if (!vuote.isEmpty()) {
            throw new IllegalArgumentException("mancano " + String.join(", ", vuote));
        }

        Double lat = coordinata(valori.get(Colonna.LAT), "latitudine", -90, 90);
        Double lng = coordinata(valori.get(Colonna.LNG), "longitudine", -180, 180);
        if ((lat == null) != (lng == null)) {
            throw new IllegalArgumentException("latitudine e longitudine vanno date insieme");
        }

        return new RigaExcel(
                numero,
                valori.get(Colonna.NOME_SOCIETA),
                valori.get(Colonna.NOME_IMPIANTO),
                valori.get(Colonna.INDIRIZZO),
                valori.getOrDefault(Colonna.LOCALITA, ""),
                valori.getOrDefault(Colonna.PROVINCIA, "").toUpperCase(),
                lat,
                lng);
    }

    /** Accetta sia "41,89" sia "41.89": dipende dalla lingua del file. */
    private static Double coordinata(String valore, String nome, double minimo, double massimo) {
        if (valore == null || valore.isEmpty()) {
            return null;
        }
        try {
            double numero = Double.parseDouble(valore.replace(',', '.'));
            if (numero < minimo || numero > massimo) {
                throw new IllegalArgumentException(nome + " fuori scala: " + valore);
            }
            return numero;
        } catch (NumberFormatException eccezione) {
            throw new IllegalArgumentException(nome + " non numerica: " + valore);
        }
    }

    /**
     * Il testo come lo vede chi apre il file: un civico scritto come numero
     * resta "12" e non diventa "12.0".
     */
    private static String testo(
            Row riga, int posizione, DataFormatter formattatore, FormulaEvaluator valutatore) {
        if (riga == null) {
            return "";
        }
        Cell cella = riga.getCell(posizione);
        if (cella == null) {
            return "";
        }
        return formattatore.formatCellValue(cella, valutatore).trim().replaceAll("\\s+", " ");
    }
}
