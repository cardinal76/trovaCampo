package it.trovacampo.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.dominio.Campionato;
import it.trovacampo.api.dominio.TipoCampionato;
import it.trovacampo.api.web.DatiNonValidiException;
import it.trovacampo.api.web.ModificaSocietaRequest;
import it.trovacampo.api.web.NuovoCampoRequest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SocietaServiceTest {

    private final SocietaRepository repository = mock(SocietaRepository.class);

    private SocietaService serviceCon(Geocoding geocoding) {
        return new SocietaService(repository, geocoding);
    }

    private SocietaService service() {
        return serviceCon(indirizzo -> Optional.empty());
    }

    @Test
    void nonInterrogaIlDatabaseSeIlTermineEVuoto() {
        assertThat(service().cerca("   ")).isEmpty();
        verify(repository, never()).cercaPerTesto(anyString());
    }

    @Test
    void cercaConIlTermineNormalizzato() {
        when(repository.cercaPerTesto(anyString())).thenReturn(List.of());

        service().cerca("  CertosÀ  ");

        verify(repository).cercaPerTesto("certosa");
    }

    @Test
    void neutralizzaIMetacaratteriDelTermineCercato() {
        when(repository.cercaPerTesto(anyString())).thenReturn(List.of());

        service().cerca("Almas Roma S.r.l.");

        verify(repository).cercaPerTesto("almas roma s\\.r\\.l\\.");
    }

    @Test
    void salvaIlCampoConLeCoordinateQuandoLaGeocodificaRiesce() {
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));
        SocietaService service =
                serviceCon(indirizzo -> Optional.of(new Geocoding.Coordinate(41.9, 12.5)));

        Societa salvata =
                service.inserisci(
                        new NuovoCampoRequest(
                                " Certosa Calcio ", " Campo Certosa ", " Via della Certosa 12 "));

        assertThat(salvata.getNomeSocieta()).isEqualTo("Certosa Calcio");
        assertThat(salvata.getNomeImpianto()).isEqualTo("Campo Certosa");
        assertThat(salvata.getLat()).isEqualTo(41.9);
        assertThat(salvata.getLng()).isEqualTo(12.5);
    }

    @Test
    void salvaIlCampoAncheSenzaCoordinateSeLaGeocodificaFallisce() {
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));

        Societa salvata =
                service().inserisci(
                                new NuovoCampoRequest(
                                        "Certosa Calcio", "Campo Certosa", "Indirizzo inventato"));

        assertThat(salvata.getLat()).isNull();
        assertThat(salvata.getLng()).isNull();
    }

    @Test
    void ilCampoInseritoDiventaRicercabileConIlTestoNormalizzato() {
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));
        ArgumentCaptor<Societa> catturata = ArgumentCaptor.forClass(Societa.class);

        service().inserisci(
                        new NuovoCampoRequest(
                                "Città di Roma", "Campo Città", "Via Verdì 1"));

        verify(repository).save(catturata.capture());
        assertThat(catturata.getValue().getTestoRicerca())
                .isEqualTo("citta di roma | campo citta | via verdi 1");
    }

    @Test
    void restituisceLaSocietaPerId() {
        Societa societa = new Societa().setId("1").setNomeSocieta("Certosa Calcio");
        when(repository.findById("1")).thenReturn(Optional.of(societa));

        assertThat(service().perId("1")).containsSame(societa);
    }

    @Test
    void elencaTuttiICampiDalRepository() {
        List<Societa> campi = List.of(new Societa().setNomeSocieta("Certosa Calcio"));
        when(repository.tuttiICampi()).thenReturn(campi);

        assertThat(service().tuttiICampi()).isSameAs(campi);
    }

    private Societa salvata() {
        return new Societa()
                .setId("1")
                .setSiglaSocieta("A.S.D.")
                .setNomeSocieta("Certosa Calcio")
                .setNomeImpianto("Campo Certosa")
                .setIndirizzoImpianto("Via della Certosa 12")
                .setLocalitaImpianto("Roma")
                .setProvinciaImpianto("RM")
                .setLat(41.89)
                .setLng(12.48)
                .setPresidente("Mario Rossi");
    }

    private static ModificaSocietaRequest modulo(String indirizzo, Double lat, Double lng) {
        return new ModificaSocietaRequest(
                " A.S.D. ", "Certosa Calcio ", "LAZIO", "Campo Certosa", indirizzo, "Roma", "rm",
                lat, lng, "4521", "  ", null, "06 1234", null, "info@certosa.it", null,
                true, "180 euro",
                List.of(
                        new Campionato("Terza Categoria", "B", "LAZIO", null),
                        new Campionato("  ", "", "", TipoCampionato.SCUOLA_CALCIO)));
    }

    private Societa modifica(ModificaSocietaRequest richiesta) {
        when(repository.findById("1")).thenReturn(Optional.of(salvata()));
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));
        return service().modifica("1", richiesta).orElseThrow();
    }

    @Test
    void laModificaSostituisceLaSchedaEPulisceIDati() {
        Societa modificata = modifica(modulo("Via della Certosa 12", 41.89, 12.48));

        assertThat(modificata.getSiglaSocieta()).isEqualTo("A.S.D.");
        assertThat(modificata.getNomeSocieta()).isEqualTo("Certosa Calcio");
        assertThat(modificata.getProvinciaImpianto()).isEqualTo("RM");
        // Un campo svuotato nel modulo sparisce dalla scheda.
        assertThat(modificata.getPresidente()).isNull();
        assertThat(modificata.getTelefono()).isEqualTo("06 1234");
        // Le righe vuote dei campionati si scartano, il tipo mancante è agonistica.
        assertThat(modificata.getCampionati())
                .containsExactly(new Campionato("Terza Categoria", "B", "LAZIO", TipoCampionato.AGONISTICA));
        assertThat(modificata.getTestoRicerca()).contains("certosa calcio");
        // Stesso indirizzo e stesse coordinate: il segnaposto resta.
        assertThat(modificata.getLat()).isEqualTo(41.89);
    }

    @Test
    void cambiandoLIndirizzoLeCoordinateSiRicalcolano() {
        Societa modificata = modifica(modulo("Via della Certosa 99", 41.89, 12.48));

        assertThat(modificata.getLat()).isNull();
        assertThat(modificata.getLng()).isNull();
    }

    @Test
    void coordinateCorretteAManoValgonoAncheSeCambiaLIndirizzo() {
        Societa modificata = modifica(modulo("Via della Certosa 99", 41.9, 12.5));

        assertThat(modificata.getLat()).isEqualTo(41.9);
        assertThat(modificata.getLng()).isEqualTo(12.5);
        assertThat(modificata.getGeocodificaFallitaVersione()).isNull();
    }

    @Test
    void ilSegnapostoCorrettoAManoNonEPiuApprossimato() {
        when(repository.findById("1")).thenReturn(Optional.of(salvata().setPosizioneApprossimata(true)));
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));

        Societa modificata =
                service().modifica("1", modulo("Via della Certosa 12", 41.9, 12.5)).orElseThrow();

        assertThat(modificata.getPosizioneApprossimata()).isNull();
    }

    @Test
    void lasciandoIlSegnapostoComEraRestaApprossimato() {
        when(repository.findById("1")).thenReturn(Optional.of(salvata().setPosizioneApprossimata(true)));
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));

        Societa modificata =
                service().modifica("1", modulo("Via della Certosa 12", 41.89, 12.48)).orElseThrow();

        // Cambiare il telefono non rende preciso il segnaposto: resta nel filtro.
        assertThat(modificata.getPosizioneApprossimata()).isTrue();
    }

    @Test
    void coordinateSvuotateSiRicalcolano() {
        assertThat(modifica(modulo("Via della Certosa 12", null, null)).getLat()).isNull();
    }

    @Test
    void senzaScuolaCalcioNonRestanoIPrezzi() {
        ModificaSocietaRequest m = modulo("Via della Certosa 12", 41.89, 12.48);
        ModificaSocietaRequest senza =
                new ModificaSocietaRequest(
                        m.siglaSocieta(), m.nomeSocieta(), m.comitatoRegionale(), m.nomeImpianto(),
                        m.indirizzoImpianto(), m.localitaImpianto(), m.provinciaImpianto(), m.lat(), m.lng(),
                        m.matricola(), m.presidente(), m.indirizzoSede(), m.telefono(), m.fax(), m.email(),
                        m.sitoWeb(), false, "180 euro", m.campionati());

        assertThat(modifica(senza).getPrezziScuolaCalcio()).isNull();
    }

    @Test
    void latitudineSenzaLongitudineERifiutata() {
        assertThatThrownBy(() -> service().modifica("1", modulo("Via 1", 41.9, null)))
                .isInstanceOf(DatiNonValidiException.class)
                .hasMessage("latitudine e longitudine vanno date insieme");
    }

    @Test
    void unaSocietaCheNonEsisteNonSiModifica() {
        when(repository.findById("x")).thenReturn(Optional.empty());

        assertThat(service().modifica("x", modulo("Via 1", null, null))).isEmpty();
        verify(repository, never()).save(any());
    }

    @Test
    void creaUnaSocietaDalModulo() {
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));

        Societa creata = service().crea(modulo("Via Nuova 1", null, null));

        assertThat(creata.getId()).isNull();
        assertThat(creata.getNomeSocieta()).isEqualTo("Certosa Calcio");
        assertThat(creata.getIndirizzoImpianto()).isEqualTo("Via Nuova 1");
        // Senza coordinate ci penserà la geocodifica automatica.
        assertThat(creata.getLat()).isNull();
        assertThat(creata.getTestoRicerca()).contains("via nuova 1");
    }

    @Test
    void unaSocietaNuovaTieneLeCoordinateDelModulo() {
        when(repository.save(any())).thenAnswer(invocazione -> invocazione.getArgument(0));

        Societa creata = service().crea(modulo("Via Nuova 1", 41.9, 12.5));

        assertThat(creata.getLat()).isEqualTo(41.9);
        assertThat(creata.getLng()).isEqualTo(12.5);
    }

    @Test
    void eliminaSoloUnaSocietaCheEsiste() {
        when(repository.existsById("1")).thenReturn(true);
        when(repository.existsById("x")).thenReturn(false);

        assertThat(service().elimina("1")).isTrue();
        assertThat(service().elimina("x")).isFalse();
        verify(repository).deleteById("1");
        verify(repository, never()).deleteById("x");
    }
}
