package it.trovacampo.api.prospect;

import static org.assertj.core.api.Assertions.assertThat;

import it.trovacampo.api.indirizzi.FonteIndirizzo;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class EsportazioneProspectTest {

    @Test
    void unProspectPerRigaConFontiEAttribuzione() throws Exception {
        Prospect p =
                new Prospect()
                        .setCodiceFiscale("96154220584")
                        .setDenominazione("A.S.D. VIS SUBIACO")
                        .setRegione("Lazio")
                        .setComune("Subiaco")
                        .setOrganismi(List.of("FIGC", "CSI"))
                        .setCalcio(true)
                        .setTesseratiAttivi(180)
                        .setIndirizzo("VIA DEI MONASTERI 1")
                        .setLat(41.92)
                        .setLng(13.09)
                        .setFonteIndirizzo(FonteIndirizzo.CAMPO_TROVACAMPO);
        ByteArrayOutputStream uscita = new ByteArrayOutputStream();

        EsportazioneProspect.scrivi(Stream.of(p), uscita);

        try (XSSFWorkbook libro = new XSSFWorkbook(new ByteArrayInputStream(uscita.toByteArray()))) {
            Sheet foglio = libro.getSheet("Prospect");
            List<String> intestazioni = new ArrayList<>();
            foglio.getRow(0).forEach(c -> intestazioni.add(c.getStringCellValue()));
            assertThat(intestazioni).isEqualTo(EsportazioneProspect.COLONNE);
            assertThat(intestazioni).noneMatch(c -> c.toLowerCase().contains("legale"));

            Row riga = foglio.getRow(1);
            assertThat(riga.getCell(0).getStringCellValue()).isEqualTo("96154220584");
            assertThat(riga.getCell(5).getStringCellValue()).isEqualTo("FIGC, CSI");
            assertThat(riga.getCell(8).getNumericCellValue()).isEqualTo(180);
            assertThat(riga.getCell(17).getStringCellValue()).isEqualTo("CAMPO_TROVACAMPO");
            assertThat(foglio.getLastRowNum()).isEqualTo(1);

            assertThat(libro.getSheet("Fonti").getRow(0).getCell(0).getStringCellValue())
                    .contains("registro.sportesalute.eu");
        }
    }
}
