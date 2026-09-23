package it.trovacampo.api.importazione;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** Costruisce in memoria un .xlsx di prova, una riga per argomento. */
final class FileExcel {

    private FileExcel() {}

    /** Un valore Number diventa una cella numerica, tutto il resto testo; null lascia la cella vuota. */
    static byte[] conRighe(Object[]... righe) {
        try (Workbook cartella = new XSSFWorkbook();
                ByteArrayOutputStream uscita = new ByteArrayOutputStream()) {
            Sheet foglio = cartella.createSheet("Campi");
            for (int r = 0; r < righe.length; r++) {
                Row riga = foglio.createRow(r);
                for (int c = 0; c < righe[r].length; c++) {
                    Object valore = righe[r][c];
                    if (valore instanceof Number numero) {
                        riga.createCell(c).setCellValue(numero.doubleValue());
                    } else if (valore != null) {
                        riga.createCell(c).setCellValue(valore.toString());
                    }
                }
            }
            cartella.write(uscita);
            return uscita.toByteArray();
        } catch (IOException eccezione) {
            throw new UncheckedIOException(eccezione);
        }
    }

    static InputStream flusso(Object[]... righe) {
        return new ByteArrayInputStream(conRighe(righe));
    }

    static Object[] riga(Object... valori) {
        return valori;
    }
}
