package it.trovacampo.api.importazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LettoreComunicatoTest {

    private final LettoreComunicato lettore = new LettoreComunicato();

    /** Il Comunicato Ufficiale n. 20 del CRL LND Lazio, stagione 2026/27, così com'è. */
    private LettoreExcel.Lettura comunicatoVero() throws Exception {
        try (InputStream pdf = getClass().getResourceAsStream("/comunicati/cu20-lnd-lazio-2026.pdf")) {
            return lettore.leggi(pdf);
        }
    }

    @Test
    void leggeIlComunicatoVero() throws Exception {
        LettoreExcel.Lettura lettura = comunicatoVero();

        // 59 campi distinti: le squadre che giocano in casa in più campionati
        // sullo stesso campo contano una volta sola.
        assertThat(lettura.righe()).hasSize(59);
        assertThat(lettura.righe().getFirst())
                .isEqualTo(
                        new RigaExcel(
                                1, "BOREALE", "DON ORIONE", "VIA DELLA CAMILLUCCIA 120", "ROMA", "", null, null));
        assertThat(lettura.colonneIgnorate()).isEmpty();
    }

    @Test
    void scartaICampiDaDefinire() throws Exception {
        assertThat(comunicatoVero().scarti())
                .extracting(Scarto::motivo)
                .containsExactly(
                        "POMEZIA CALCIO 1957: campo da definire",
                        "SANTA MARINELLA 1947: campo da definire",
                        "F.C.PARIOLI A.S.D.: campo da definire",
                        "OSTIANTICA CALCIO 1926: campo da definire",
                        "URBETEVERE CALCIO: campo da definire");
    }

    @Test
    void togliFondoEFrazioneEAggiustaLeColonneTagliate() throws Exception {
        LettoreExcel.Lettura lettura = comunicatoVero();

        assertThat(lettura.righe())
                .filteredOn(r -> r.nomeSocieta().equals("VILLALBA OCRES MOCA 1952"))
                .singleElement()
                .satisfies(
                        r -> {
                            assertThat(r.nomeImpianto()).isEqualTo("SCROCCA RENATO");
                            assertThat(r.localita()).isEqualTo("GUIDONIA MONTECELIO");
                            assertThat(r.indirizzo()).isEqualTo("VIA PANTANE 9/11");
                        });
        // Gli spazi doppi del tabulato non finiscono nell'indirizzo.
        assertThat(lettura.righe())
                .filteredOn(r -> r.nomeSocieta().equals("R. MORANDI A.S.D."))
                .extracting(RigaExcel::indirizzo)
                .containsExactly("VIA GIOVANNI AMENDUNI 15");
    }

    @Test
    void ilComuneLasciaFuoriLaFrazione() {
        assertThat(LettoreComunicato.comune("ROMA (CAMILLUCCIA)")).isEqualTo("ROMA");
        assertThat(LettoreComunicato.comune("GUIDONIA MONTECELIO (VILLALBA")).isEqualTo("GUIDONIA MONTECELIO");
        assertThat(LettoreComunicato.comune("GUIDONIA MONTECELIO-(VILLANOVA")).isEqualTo("GUIDONIA MONTECELIO");
        assertThat(LettoreComunicato.comune("TIVOLI")).isEqualTo("TIVOLI");
    }

    @Test
    void scartaUnaRigaCheNonRispettaIlTracciato() {
        String testo =
                " 1)  BOREALE                          LUISS                            190  DON ORIONE\n"
                        + "                                                                            VIA DELLA CAMILLUCCIA 120\n";

        assertThat(lettore.leggiTesto(testo).scarti())
                .extracting(Scarto::motivo)
                .containsExactly("BOREALE: riga non nel formato del programma gare");
    }

    @Test
    void rifiutaUnPdfSenzaProgrammaGare() {
        assertThatThrownBy(() -> lettore.leggiTesto("Comunicato Ufficiale\nNessuna gara\n"))
                .isInstanceOf(LettoreExcel.FileNonValidoException.class)
                .hasMessageContaining("nessuna partita trovata");
    }

    @Test
    void rifiutaUnPdfIllegibile() {
        InputStream finto = new ByteArrayInputStream("%PDF-1.4 rotto".getBytes(StandardCharsets.US_ASCII));

        assertThatThrownBy(() -> lettore.leggi(finto))
                .isInstanceOf(LettoreExcel.FileNonValidoException.class)
                .hasMessage("Il PDF non si riesce a leggere");
    }
}
