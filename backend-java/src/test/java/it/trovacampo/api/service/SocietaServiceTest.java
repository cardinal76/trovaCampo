package it.trovacampo.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
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
}
