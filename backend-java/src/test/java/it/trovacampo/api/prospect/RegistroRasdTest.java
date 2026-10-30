package it.trovacampo.api.prospect;

import static it.trovacampo.api.prospect.RispostaRasd.dettaglio;
import static it.trovacampo.api.prospect.RispostaRasd.elenco;
import static it.trovacampo.api.prospect.RispostaRasd.riga;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class RegistroRasdTest {

    static final String URL = "https://registro.test";
    static final String UA = "TrovaCampo-prospect/test (+https://trovacampo.footballer.it)";

    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private long adesso = 0;
    private final List<Long> attese = new ArrayList<>();
    private final LimitatoreRichieste limitatore =
            new LimitatoreRichieste(
                    Duration.ofSeconds(1),
                    () -> adesso,
                    millis -> {
                        attese.add(millis);
                        adesso += millis * 1_000_000;
                    });
    private final RegistroRasd registro =
            new RegistroRasd(RegistroRasd.configura(builder, URL, UA).build(), limitatore);

    static String lista(int regione, int start) {
        return URL + "/api/istruttoria/lista/?chiamante=registroPubblico_n"
                + "&istruttoria_societa_sedeLegale_regione_id_f=" + regione
                + "&start=" + start + "&length=100";
    }

    @Test
    void leggeUnaPaginaDellElencoConUserAgentIdentificato() throws Exception {
        server.expect(requestTo(lista(12, 0)))
                .andExpect(header("User-Agent", UA))
                .andRespond(
                        withSuccess(
                                elenco(823, riga(262318, "96154220584", "A.S.D. VIS SUBIACO", "Subiaco", "FIGC", "CSI")),
                                MediaType.APPLICATION_JSON));

        RegistroRasd.Pagina pagina = registro.pagina(12, 0, 100);

        assertThat(pagina.totale()).isEqualTo(823);
        assertThat(pagina.voci())
                .containsExactly(
                        new RegistroRasd.Voce(
                                262318, "96154220584", "A.S.D. VIS SUBIACO", "Lazio", "Subiaco",
                                List.of("FIGC", "CSI"), "Associazione senza personalità giuridica",
                                LocalDate.of(2011, 12, 22)));
        server.verify();
    }

    @Test
    void delDettaglioLeggeSoloLeVociDellaSocietaENonIlLegaleRappresentante() throws Exception {
        server.expect(requestTo(URL + "/api/istruttoria/262318/sidebar/?chiamante=registroPubblico_n"))
                .andRespond(
                        withSuccess(
                                dettaglio("96154220584", "Calcio a 11, Calcio a 5", 210),
                                MediaType.APPLICATION_JSON));

        RegistroRasd.Dettaglio dettaglio = registro.dettaglio(262318);

        assertThat(dettaglio)
                .isEqualTo(new RegistroRasd.Dettaglio(List.of("Calcio a 11", "Calcio a 5"), 210, 3, 120));
        assertThat(dettaglio.toString()).doesNotContain(RispostaRasd.LEGALE_RAPPRESENTANTE);
        assertThat(RegistroRasd.VOCI_DETTAGLIO).noneMatch(v -> v.toLowerCase().contains("legale"));
    }

    @Test
    void ogniRichiestaPassaDalLimitatore() throws Exception {
        for (int start : new int[] {0, 100, 200}) {
            server.expect(requestTo(lista(12, start)))
                    .andRespond(withSuccess(elenco(300), MediaType.APPLICATION_JSON));
        }

        registro.pagina(12, 0, 100);
        registro.pagina(12, 100, 100);
        registro.pagina(12, 200, 100);

        // Tre richieste di fila: due attese di un secondo.
        assertThat(attese).containsExactly(1000L, 1000L);
    }

    @Test
    void unRifiutoFermaTuttoSenzaRiprovare() {
        server.expect(requestTo(lista(12, 0))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> registro.pagina(12, 0, 100))
                .isInstanceOf(RegistroRasd.RegistroBloccatoException.class)
                .hasMessageContaining("429");
        server.verify();
    }

    @Test
    void unaPaginaHtmlAlPostoDelJsonEUnaProtezioneAntiBot() {
        server.expect(requestTo(lista(12, 0)))
                .andRespond(withSuccess("<html><body>captcha</body></html>", MediaType.TEXT_HTML));

        assertThatThrownBy(() -> registro.pagina(12, 0, 100))
                .isInstanceOf(RegistroRasd.RegistroBloccatoException.class)
                .hasMessageContaining("anti-bot");
    }
}
