package it.trovacampo.api.importazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.service.Geocoding;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GeocodificaAutomaticaTest {

    private final SocietaRepository repository = mock(SocietaRepository.class);

    private Societa senzaCoordinate() {
        return new Societa()
                .setNomeSocieta("Virtus Ostia")
                .setIndirizzoImpianto("Via delle Baleniere 5")
                .setLocalitaImpianto("Ostia")
                .setProvinciaImpianto("RM");
    }

    @Test
    void cercaLIndirizzoCompletoDiLocalitaEPaese() {
        AtomicReference<String> cercato = new AtomicReference<>();
        Societa societa = senzaCoordinate();
        when(repository.primaDaGeocodificare()).thenReturn(Optional.of(societa));

        new GeocodificaAutomatica(
                        repository,
                        indirizzo -> {
                            cercato.set(indirizzo);
                            return Optional.of(new Geocoding.Coordinate(41.73, 12.28));
                        })
                .geocodificaLaProssima();

        assertThat(cercato).hasValue("Via delle Baleniere 5, Ostia RM, Italia");
        assertThat(societa.getLat()).isEqualTo(41.73);
        assertThat(societa.getLng()).isEqualTo(12.28);
        verify(repository).save(societa);
    }

    @Test
    void segnaGliIndirizziNonRiconosciutiPerNonRiprovarliAllInfinito() {
        Societa societa = senzaCoordinate();
        when(repository.primaDaGeocodificare()).thenReturn(Optional.of(societa));

        new GeocodificaAutomatica(repository, indirizzo -> Optional.empty()).geocodificaLaProssima();

        assertThat(societa.getGeocodificaFallita()).isTrue();
        assertThat(societa.getLat()).isNull();
        verify(repository).save(societa);
    }

    @Test
    void nonFaNienteSeNonCeNullaDaGeocodificare() {
        when(repository.primaDaGeocodificare()).thenReturn(Optional.empty());

        new GeocodificaAutomatica(repository, indirizzo -> Optional.empty()).geocodificaLaProssima();

        verify(repository, never()).save(any());
    }

    @Test
    void unErroreDelDatabaseNonFermaLoScheduler() {
        when(repository.primaDaGeocodificare()).thenThrow(new IllegalStateException("mongo giù"));

        new GeocodificaAutomatica(repository, indirizzo -> Optional.empty()).geocodificaLaProssima();

        verify(repository, never()).save(any());
    }

    @Test
    void senzaLocalitaUsaIndirizzoEPaese() {
        Societa societa = new Societa().setIndirizzoImpianto("Via Roma 1").setLocalitaImpianto("");

        assertThat(GeocodificaAutomatica.indirizzoCompleto(societa)).isEqualTo("Via Roma 1, Italia");
    }
}
