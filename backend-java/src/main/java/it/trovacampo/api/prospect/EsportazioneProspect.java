package it.trovacampo.api.prospect;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Service;

/**
 * {@code prospect_italia.xlsx}: un foglio con un prospect per riga e uno con
 * fonti e attribuzioni. Scritto in streaming (SXSSF): a livello nazionale
 * sono centomila righe, che in memoria tutte insieme peserebbero troppo.
 *
 * <p>Nessuna colonna per persone fisiche: il legale rappresentante non c'è
 * nemmeno in archivio.
 */
@Service
public class EsportazioneProspect {

    static final String NOME_FILE = "prospect_italia.xlsx";

    static final List<String> COLONNE =
            List.of(
                    "Codice fiscale",
                    "Denominazione",
                    "Natura giuridica",
                    "Regione",
                    "Comune sede",
                    "Organismi affilianti",
                    "Calcio",
                    "Discipline attive",
                    "Tesserati attivi",
                    "Attività sportive ultimo anno",
                    "Partecipazioni ultimo anno",
                    "Data iscrizione RASD",
                    "Impianto",
                    "Indirizzo campo",
                    "Comune campo",
                    "Lat",
                    "Lng",
                    "Fonte indirizzo",
                    "Confidenza indirizzo");

    static final List<String> FONTI =
            List.of(
                    "Società: Registro nazionale delle attività sportive dilettantistiche (RASD),"
                            + " Sport e Salute S.p.A., parte pubblica https://registro.sportesalute.eu/#/registro/n/",
                    "Solo dati della persona giuridica: nessun dato del legale rappresentante.",
                    "CAMPO_TROVACAMPO: campi di TrovaCampo (comunicati ufficiali LND, file importati, correzioni a mano).",
                    "CAMPO_PRESENZE: anagrafica di presenze, dai Comunicati Ufficiali del Comitato Regionale Lazio LND.",
                    "OPEN_DATA: Censimento impianti sportivi di Roma e Provincia, Città metropolitana di Roma Capitale,"
                            + " licenza CC0 1.0 (https://opendata.cittametropolitanaroma.it).",
                    "GEOCODIFICA: campo trovato su OpenStreetMap per nome e comune, posizione indicativa"
                            + " (confidenza bassa).",
                    "Coordinate e indirizzi geocodificati: © OpenStreetMap contributors, dati ODbL"
                            + " (https://www.openstreetmap.org/copyright).");

    private final ProspectRepository repository;

    public EsportazioneProspect(ProspectRepository repository) {
        this.repository = repository;
    }

    public void scrivi(OutputStream uscita) throws IOException {
        try (Stream<Prospect> tutti = repository.tuttiInOrdine()) {
            scrivi(tutti, uscita);
        }
    }

    static void scrivi(Stream<Prospect> prospect, OutputStream uscita) throws IOException {
        SXSSFWorkbook libro = new SXSSFWorkbook(500);
        try {
            CellStyle intestazione = libro.createCellStyle();
            Font grassetto = libro.createFont();
            grassetto.setBold(true);
            intestazione.setFont(grassetto);

            Sheet foglio = libro.createSheet("Prospect");
            Row prima = foglio.createRow(0);
            for (int i = 0; i < COLONNE.size(); i++) {
                Cell cella = prima.createCell(i);
                cella.setCellValue(COLONNE.get(i));
                cella.setCellStyle(intestazione);
            }
            foglio.createFreezePane(0, 1);

            int[] riga = {1};
            prospect.forEach(p -> scriviRiga(foglio.createRow(riga[0]++), p));

            Sheet fonti = libro.createSheet("Fonti");
            for (int i = 0; i < FONTI.size(); i++) {
                fonti.createRow(i).createCell(0).setCellValue(FONTI.get(i));
            }
            libro.write(uscita);
        } finally {
            libro.close();
        }
    }

    private static void scriviRiga(Row riga, Prospect p) {
        int i = 0;
        testo(riga, i++, p.getCodiceFiscale());
        testo(riga, i++, p.getDenominazione());
        testo(riga, i++, p.getNaturaGiuridica());
        testo(riga, i++, p.getRegione());
        testo(riga, i++, p.getComune());
        testo(riga, i++, unisci(p.getOrganismi()));
        testo(riga, i++, p.isCalcio() ? "sì" : "no");
        testo(riga, i++, unisci(p.getDiscipline()));
        numero(riga, i++, p.getTesseratiAttivi());
        numero(riga, i++, p.getAttivitaSportiveUltimoAnno());
        numero(riga, i++, p.getPartecipazioniUltimoAnno());
        testo(riga, i++, p.getDataIscrizione() == null ? null : p.getDataIscrizione().toString());
        testo(riga, i++, p.getNomeImpianto());
        testo(riga, i++, p.getIndirizzo());
        testo(riga, i++, p.getComuneCampo());
        numero(riga, i++, p.getLat());
        numero(riga, i++, p.getLng());
        testo(riga, i++, p.getFonteIndirizzo() == null ? null : p.getFonteIndirizzo().name());
        testo(riga, i, p.getConfidenzaIndirizzo() == null ? null : p.getConfidenzaIndirizzo().name());
    }

    private static String unisci(List<String> valori) {
        return valori == null ? null : String.join(", ", valori);
    }

    private static void testo(Row riga, int colonna, String valore) {
        if (valore != null) {
            riga.createCell(colonna).setCellValue(valore);
        }
    }

    private static void numero(Row riga, int colonna, Number valore) {
        if (valore != null) {
            riga.createCell(colonna).setCellValue(valore.doubleValue());
        }
    }
}
