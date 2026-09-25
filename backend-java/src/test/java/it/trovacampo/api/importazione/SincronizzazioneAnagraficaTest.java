package it.trovacampo.api.importazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

import it.trovacampo.api.anagrafica.AnagraficaPresenze;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SincronizzazioneAnagraficaTest {

    private final SocietaRepository repository = mock(SocietaRepository.class);
    private final AnagraficaPresenze anagrafica = mock(AnagraficaPresenze.class);
    private final SincronizzazioneAnagrafica sincronizzazione =
            new SincronizzazioneAnagrafica(anagrafica, new ImportazioneService(repository));

    private static AnagraficaPresenze.Riferimento societa(long id, String nome) {
        return new AnagraficaPresenze.Riferimento(id, nome);
    }

    private List<Societa> salvate() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Societa>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void ogniSocietaDiUnCampoDiventaUnaRigaLegataAllAnagrafica() {
        // Già in TrovaCampo da un vecchio PDF, con le coordinate trovate dalla geocodifica.
        Societa boreale =
                new Societa()
                        .setId("m1")
                        .setNomeSocieta("BOREALE")
                        .setNomeImpianto("DON ORIONE")
                        .setIndirizzoImpianto("VIA DELLA CAMILLUCCIA 120")
                        .setLocalitaImpianto("ROMA")
                        .setProvinciaImpianto("")
                        .setLat(41.9)
                        .setLng(12.4);
        when(repository.findAll()).thenReturn(List.of(boreale));
        when(anagrafica.impianti())
                .thenReturn(
                        List.of(
                                new AnagraficaPresenze.Impianto(
                                        190L, "DON ORIONE", "VIA DELLA CAMILLUCCIA 120", "ROMA", null,
                                        null, List.of(societa(7, "BOREALE"), societa(8, "LODIGIANI"))),
                                new AnagraficaPresenze.Impianto(
                                        191L, "COMUNALE", null, "ARCE", null, null,
                                        List.of(societa(9, "ARCE 1932"))),
                                new AnagraficaPresenze.Impianto(
                                        192L, "VUOTO", "VIA ROMA 1", "SORA", null, null, List.of())));

        EsitoImportazione esito = sincronizzazione.sincronizza(false);

        assertThat(esito.aggiornate()).isEqualTo(1);
        assertThat(esito.inserite()).isEqualTo(1);
        assertThat(esito.scartate())
                .extracting(Scarto::motivo)
                .containsExactly("ARCE 1932: il campo COMUNALE non ha indirizzo");

        assertThat(salvate())
                .satisfiesExactly(
                        vecchia -> {
                            assertThat(vecchia.getId()).isEqualTo("m1");
                            assertThat(vecchia.getAnagraficaSocietaId()).isEqualTo(7L);
                            // L'id del campo in presenze: la chiave delle partite.
                            assertThat(vecchia.getAnagraficaImpiantoId()).isEqualTo(190L);
                            // Presenze non ha coordinate e il campo non si è spostato: restano.
                            assertThat(vecchia.getLat()).isEqualTo(41.9);
                            // Presenze dà solo il comune: la provincia si ricava da lì.
                            assertThat(vecchia.getProvinciaImpianto()).isEqualTo("RM");
                        },
                        nuova -> {
                            assertThat(nuova.getNomeSocieta()).isEqualTo("LODIGIANI");
                            assertThat(nuova.getAnagraficaSocietaId()).isEqualTo(8L);
                            assertThat(nuova.getAnagraficaImpiantoId()).isEqualTo(190L);
                            assertThat(nuova.getLocalitaImpianto()).isEqualTo("ROMA");
                            assertThat(nuova.getProvinciaImpianto()).isEqualTo("RM");
                            assertThat(nuova.getLat()).isNull();
                        });
    }

    @Test
    void laProvaNonSalvaNiente() {
        when(repository.findAll()).thenReturn(List.of());
        when(anagrafica.impianti())
                .thenReturn(
                        List.of(
                                new AnagraficaPresenze.Impianto(
                                        190L, "DON ORIONE", "VIA DELLA CAMILLUCCIA 120", "ROMA", 41.93,
                                        12.44, List.of(societa(7, "BOREALE")))));

        EsitoImportazione esito = sincronizzazione.sincronizza(true);

        assertThat(esito.prova()).isTrue();
        assertThat(esito.inserite()).isEqualTo(1);
        assertThat(esito.daGeocodificare()).isZero();
        verify(repository, never()).saveAll(any());
    }

    /**
     * Lo stemma arriva con la società: uno https si prende, uno che presenze
     * non manda non cancella quello che c'è, e uno che non è https si scarta.
     */
    @Test
    void loStemmaSiPrendeSeHttpsENonSiCancella() {
        String stemma = "https://play.lnd.it/lndimg/111/111-web.png";
        Societa conStemma =
                new Societa()
                        .setId("m1")
                        .setNomeSocieta("BOREALE")
                        .setNomeImpianto("DON ORIONE")
                        .setIndirizzoImpianto("VIA DELLA CAMILLUCCIA 120")
                        .setLocalitaImpianto("ROMA")
                        .setProvinciaImpianto("RM")
                        .setAnagraficaSocietaId(7L)
                        .setAnagraficaImpiantoId(190L)
                        .setLogoUrl(stemma)
                        .setLat(41.9)
                        .setLng(12.4);
        when(repository.findAll()).thenReturn(List.of(conStemma));
        when(anagrafica.impianti())
                .thenReturn(
                        List.of(
                                new AnagraficaPresenze.Impianto(
                                        190L, "DON ORIONE", "VIA DELLA CAMILLUCCIA 120", "ROMA", null,
                                        null,
                                        List.of(
                                                // Presenze non lo manda: resta quello di prima.
                                                societa(7, "BOREALE"),
                                                new AnagraficaPresenze.Riferimento(
                                                        8L, "LODIGIANI", " " + stemma + " "),
                                                new AnagraficaPresenze.Riferimento(
                                                        9L, "TOR SAPIENZA",
                                                        "http://play.lnd.it/lndimg/9/9-web.png"),
                                                new AnagraficaPresenze.Riferimento(
                                                        10L, "VIGOR", "javascript:alert(1)")))));

        EsitoImportazione esito = sincronizzazione.sincronizza(false);

        assertThat(esito.invariate()).isEqualTo(1);
        assertThat(salvate())
                .extracting(Societa::getNomeSocieta, Societa::getLogoUrl)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("LODIGIANI", stemma),
                        org.assertj.core.groups.Tuple.tuple("TOR SAPIENZA", null),
                        org.assertj.core.groups.Tuple.tuple("VIGOR", null));
    }

    @Test
    void unoStemmaNuovoAggiornaLaSocieta() {
        Societa senza =
                new Societa()
                        .setId("m1")
                        .setNomeSocieta("BOREALE")
                        .setNomeImpianto("DON ORIONE")
                        .setIndirizzoImpianto("VIA DELLA CAMILLUCCIA 120")
                        .setLocalitaImpianto("ROMA")
                        .setProvinciaImpianto("RM")
                        .setAnagraficaSocietaId(7L)
                        .setAnagraficaImpiantoId(190L);
        when(repository.findAll()).thenReturn(List.of(senza));
        when(anagrafica.impianti())
                .thenReturn(
                        List.of(
                                new AnagraficaPresenze.Impianto(
                                        190L, "DON ORIONE", "VIA DELLA CAMILLUCCIA 120", "ROMA", null,
                                        null,
                                        List.of(new AnagraficaPresenze.Riferimento(
                                                7L, "BOREALE", "https://play.lnd.it/lndimg/1/1-web.jpg")))));

        EsitoImportazione esito = sincronizzazione.sincronizza(false);

        assertThat(esito.aggiornate()).isEqualTo(1);
        assertThat(salvate().get(0).getLogoUrl()).isEqualTo("https://play.lnd.it/lndimg/1/1-web.jpg");
    }
}
