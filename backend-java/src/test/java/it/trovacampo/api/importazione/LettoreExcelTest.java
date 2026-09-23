package it.trovacampo.api.importazione;

import static it.trovacampo.api.importazione.FileExcel.flusso;
import static it.trovacampo.api.importazione.FileExcel.riga;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

class LettoreExcelTest {

    private final LettoreExcel lettore = new LettoreExcel();

    @Test
    void leggeLeColonneRiconoscendoleDaiSinonimi() {
        LettoreExcel.Lettura lettura =
                lettore.leggi(
                        flusso(
                                riga("DENOMINAZIONE", "Campo di gioco", "Indirizzo", "Comune", "Prov."),
                                riga("Certosa Calcio", "Campo Certosa", "Via della Certosa 12", "Roma", "rm")));

        assertThat(lettura.scarti()).isEmpty();
        assertThat(lettura.righe())
                .containsExactly(
                        new RigaExcel(
                                2,
                                "Certosa Calcio",
                                "Campo Certosa",
                                "Via della Certosa 12",
                                "Roma",
                                "RM",
                                null,
                                null));
    }

    @Test
    void trovaLIntestazioneSottoUnTitolo() {
        LettoreExcel.Lettura lettura =
                lettore.leggi(
                        flusso(
                                riga("Elenco campi stagione 2026/27"),
                                riga(),
                                riga("Società", "Impianto", "Indirizzo"),
                                riga("Virtus Ostia", "Campo Paolucci", "Via delle Baleniere 5")));

        assertThat(lettura.righe()).singleElement().extracting(RigaExcel::numero).isEqualTo(4);
    }

    @Test
    void unCivicoNumericoNonDiventaDecimale() {
        LettoreExcel.Lettura lettura =
                lettore.leggi(
                        flusso(
                                riga("Società", "Impianto", "Indirizzo", "Località"),
                                riga("Certosa", "Campo", 12, 100)));

        assertThat(lettura.righe().getFirst().indirizzo()).isEqualTo("12");
        assertThat(lettura.righe().getFirst().localita()).isEqualTo("100");
    }

    @Test
    void leggeLeCoordinateConLaVirgolaOIlPunto() {
        LettoreExcel.Lettura lettura =
                lettore.leggi(
                        flusso(
                                riga("Società", "Impianto", "Indirizzo", "Latitudine", "Longitudine"),
                                riga("A", "Campo A", "Via A 1", "41,8919", "12.4863"),
                                riga("B", "Campo B", "Via B 1", 41.5, 12.5)));

        assertThat(lettura.righe())
                .extracting(RigaExcel::lat, RigaExcel::lng)
                .containsExactly(
                        Tuple.tuple(41.8919, 12.4863),
                        Tuple.tuple(41.5, 12.5));
    }

    @Test
    void scartaLeRigheIncompleteESaltaQuelleVuote() {
        LettoreExcel.Lettura lettura =
                lettore.leggi(
                        flusso(
                                riga("Società", "Impianto", "Indirizzo", "Lat", "Lng"),
                                riga("Certosa", "Campo Certosa", "Via Certosa 12"),
                                riga(),
                                riga("Senza campo", null, ""),
                                riga("Coordinate a metà", "Campo", "Via 1", 41.9),
                                riga("Fuori scala", "Campo", "Via 1", 141.9, 12),
                                riga("Non numerica", "Campo", "Via 1", "nord", 12)));

        assertThat(lettura.righe()).extracting(RigaExcel::nomeSocieta).containsExactly("Certosa");
        assertThat(lettura.scarti())
                .containsExactly(
                        new Scarto(4, "mancano impianto, indirizzo"),
                        new Scarto(5, "latitudine e longitudine vanno date insieme"),
                        new Scarto(6, "latitudine fuori scala: 141.9"),
                        new Scarto(7, "latitudine non numerica: nord"));
    }

    @Test
    void segnalaLeColonneNonRiconosciute() {
        LettoreExcel.Lettura lettura =
                lettore.leggi(
                        flusso(
                                riga("Società", "Impianto", "Indirizzo", "Presidente", "Note"),
                                riga("A", "B", "C", "D", "E")));

        assertThat(lettura.colonneIgnorate()).containsExactly("Presidente", "Note");
    }

    @Test
    void rifiutaUnFileSenzaLeColonneObbligatorie() {
        assertThatThrownBy(() -> lettore.leggi(flusso(riga("Società", "Città"), riga("A", "Roma"))))
                .isInstanceOf(LettoreExcel.FileNonValidoException.class)
                .hasMessage("Mancano le colonne obbligatorie: impianto, indirizzo");
    }

    @Test
    void rifiutaUnFileSenzaIntestazione() {
        assertThatThrownBy(() -> lettore.leggi(flusso(riga("Nome", "Via"), riga("A", "B"))))
                .isInstanceOf(LettoreExcel.FileNonValidoException.class)
                .hasMessageContaining("Intestazione non trovata");
    }

    /** Il modello che si dà a chi compila i file deve restare importabile. */
    @Test
    void ilModelloDelRepositoryEImportabile() throws Exception {
        try (InputStream modello =
                Files.newInputStream(Path.of("../documenti/importazione/modello-importazione.xlsx"))) {
            LettoreExcel.Lettura lettura = lettore.leggi(modello);

            assertThat(lettura.scarti()).isEmpty();
            assertThat(lettura.colonneIgnorate()).isEmpty();
            assertThat(lettura.righe()).extracting(RigaExcel::nomeSocieta)
                    .containsExactly("Certosa Calcio", "Virtus Ostia");
        }
    }

    @Test
    void rifiutaUnFileCheNonEExcel() {
        byte[] csv = "Società;Impianto;Indirizzo\nA;B;C\n".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> lettore.leggi(new ByteArrayInputStream(csv)))
                .isInstanceOf(LettoreExcel.FileNonValidoException.class)
                .hasMessageStartingWith("Il file non è un Excel leggibile");
    }
}
