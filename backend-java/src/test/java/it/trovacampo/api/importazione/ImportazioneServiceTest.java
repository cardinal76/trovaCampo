package it.trovacampo.api.importazione;

import static it.trovacampo.api.importazione.FileExcel.flusso;
import static it.trovacampo.api.importazione.FileExcel.riga;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ImportazioneServiceTest {

    private static final Object[] INTESTAZIONE =
            riga("Società", "Impianto", "Indirizzo", "Comune", "Provincia", "Lat", "Lng");

    private final SocietaRepository repository = mock(SocietaRepository.class);
    private final ImportazioneService service = new ImportazioneService(repository);

    private List<Societa> salvate() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Societa>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Societa certosa() {
        return new Societa()
                .setId("1")
                .setSiglaSocieta("A.S.D.")
                .setNomeSocieta("Certosa Calcio")
                .setNomeImpianto("Campo Certosa")
                .setIndirizzoImpianto("Via della Certosa 12")
                .setLocalitaImpianto("Roma")
                .setProvinciaImpianto("RM")
                .setLat(41.8919)
                .setLng(12.4863)
                .setPresidente("Mario Rossi");
    }

    @Test
    void inserisceLeSocietaNuoveSenzaCoordinateDaGeocodificare() {
        when(repository.findAll()).thenReturn(List.of());

        EsitoImportazione esito =
                service.importa(
                        flusso(INTESTAZIONE, riga("Virtus Ostia", "Campo Paolucci", "Via delle Baleniere 5", "Ostia", "RM")),
                        false);

        assertThat(esito.inserite()).isEqualTo(1);
        assertThat(esito.daGeocodificare()).isEqualTo(1);
        Societa nuova = salvate().getFirst();
        assertThat(nuova.getId()).isNull();
        assertThat(nuova.getNomeSocieta()).isEqualTo("Virtus Ostia");
        assertThat(nuova.getLat()).isNull();
        assertThat(nuova.getTestoRicerca()).contains("virtus ostia").contains("ostia");
    }

    @Test
    void aggiornaLaSocietaEsistenteInveceDiDuplicarla() {
        Societa esistente = certosa();
        when(repository.findAll()).thenReturn(List.of(esistente));

        EsitoImportazione esito =
                service.importa(
                        flusso(INTESTAZIONE, riga("CERTOSA CALCIO", "campo certosa", "Via della Certosa 14", "Roma", "RM")),
                        false);

        assertThat(esito.inserite()).isZero();
        assertThat(esito.aggiornate()).isEqualTo(1);
        Societa aggiornata = salvate().getFirst();
        assertThat(aggiornata.getId()).isEqualTo("1");
        assertThat(aggiornata.getIndirizzoImpianto()).isEqualTo("Via della Certosa 14");
        // Le coordinate vecchie indicherebbero il posto sbagliato.
        assertThat(aggiornata.getLat()).isNull();
        // Quello che il file non porta resta com'era.
        assertThat(aggiornata.getPresidente()).isEqualTo("Mario Rossi");
        assertThat(aggiornata.getSiglaSocieta()).isEqualTo("A.S.D.");
    }

    @Test
    void unaCellaVuotaNonCancellaIlValorePresente() {
        when(repository.findAll()).thenReturn(List.of(certosa()));

        EsitoImportazione esito =
                service.importa(
                        flusso(INTESTAZIONE, riga("Certosa Calcio", "Campo Certosa", "Via della Certosa 12")),
                        false);

        assertThat(esito.invariate()).isEqualTo(1);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void usaLeCoordinateDelFileQuandoCiSono() {
        when(repository.findAll()).thenReturn(List.of());

        EsitoImportazione esito =
                service.importa(
                        flusso(INTESTAZIONE, riga("A", "Campo A", "Via A 1", "Roma", "RM", 41.9, 12.5)),
                        false);

        assertThat(esito.daGeocodificare()).isZero();
        assertThat(salvate().getFirst().getLat()).isEqualTo(41.9);
    }

    @Test
    void unIndirizzoNuovoRidaUnaPossibilitaAllaGeocodifica() {
        Societa fallita = certosa().setLat(null).setLng(null).setGeocodificaFallita(true);
        when(repository.findAll()).thenReturn(List.of(fallita));

        service.importa(
                flusso(INTESTAZIONE, riga("Certosa Calcio", "Campo Certosa", "Via Certosa 12", "Roma", "RM")),
                false);

        assertThat(salvate().getFirst().getGeocodificaFallita()).isNull();
    }

    @Test
    void scartaLeRigheRipetuteNelloStessoFile() {
        when(repository.findAll()).thenReturn(List.of());

        EsitoImportazione esito =
                service.importa(
                        flusso(
                                INTESTAZIONE,
                                riga("A", "Campo A", "Via A 1"),
                                riga("A.", "Campo  A", "Via A 2")),
                        false);

        assertThat(esito.inserite()).isEqualTo(1);
        assertThat(esito.scartate()).containsExactly(new Scarto(3, "stessa società e impianto della riga 2"));
    }

    @Test
    void inProvaNonSalvaNiente() {
        when(repository.findAll()).thenReturn(new ArrayList<>(List.of(certosa())));

        EsitoImportazione esito =
                service.importa(
                        flusso(
                                INTESTAZIONE,
                                riga("Certosa Calcio", "Campo Certosa", "Via nuova 1"),
                                riga("Nuova", "Campo nuovo", "Via 2"),
                                riga("Senza indirizzo", "Campo")),
                        true);

        assertThat(esito.prova()).isTrue();
        assertThat(esito.righeLette()).isEqualTo(3);
        assertThat(esito.inserite()).isEqualTo(1);
        assertThat(esito.aggiornate()).isEqualTo(1);
        assertThat(esito.scartate()).extracting(Scarto::riga).containsExactly(4);
        verify(repository, never()).saveAll(any());
    }

    @Test
    void laChiaveIgnoraMaiuscoleAccentiEPunteggiatura() {
        assertThat(ImportazioneService.chiave("A.S.D. Città  Calcio", "Sant'Anna \"A\""))
                .isEqualTo(ImportazioneService.chiave("asd citta calcio", "santanna a"));
    }
}
