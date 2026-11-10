package it.trovacampo.api.importazione;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.geocoding.Geocoding;
import it.trovacampo.api.geocoding.GeocodingNonDisponibile;
import it.trovacampo.api.societa.Societa;
import it.trovacampo.api.societa.SocietaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GeocodificaAutomaticaTest {

    private final SocietaRepository repository = mock(SocietaRepository.class);

    private GeocodificaAutomatica con(Geocoding geocoding) {
        return new GeocodificaAutomatica(repository, geocoding, Duration.ZERO);
    }

    private Societa campo(String indirizzo, String localita) {
        return new Societa()
                .setNomeSocieta("Virtus Ostia")
                .setIndirizzoImpianto(indirizzo)
                .setLocalitaImpianto(localita)
                .setProvinciaImpianto("");
    }

    @Test
    void cercaLIndirizzoCompletoDiLocalitaEPaese() {
        List<String> cercati = new ArrayList<>();
        Societa societa = campo("Via delle Baleniere 5", "Ostia").setProvinciaImpianto("RM");
        when(repository.primaDaGeocodificare(GeocodificaAutomatica.VERSIONE))
                .thenReturn(Optional.of(societa));

        con(indirizzo -> {
                    cercati.add(indirizzo);
                    return Optional.of(new Geocoding.Coordinate(41.73, 12.28));
                })
                .geocodificaLaProssima();

        assertThat(cercati).containsExactly("VIA DELLE BALENIERE 5, Ostia RM, Italia");
        assertThat(societa.getLat()).isEqualTo(41.73);
        assertThat(societa.getGeocodificaFallitaVersione()).isNull();
        verify(repository).save(societa);
    }

    @Test
    void seLIndirizzoNonSiTrovaRiprovaSenzaCivico() {
        List<String> cercati = new ArrayList<>();
        Societa societa = campo("VIA DELLA CAMILLUCCIA 120", "ROMA");

        con(indirizzo -> {
                    cercati.add(indirizzo);
                    return cercati.size() == 1
                            ? Optional.empty()
                            : Optional.of(new Geocoding.Coordinate(41.93, 12.44));
                })
                .geocodifica(societa);

        assertThat(cercati)
                .containsExactly(
                        "VIA DELLA CAMILLUCCIA 120, ROMA, Italia", "VIA DELLA CAMILLUCCIA, ROMA, Italia");
        assertThat(societa.getLat()).isEqualTo(41.93);
        // Senza civico il segnaposto sta sulla via: chi amministra lo ritrova col filtro.
        assertThat(societa.getPosizioneApprossimata()).isTrue();
    }

    @Test
    void trovatoAlPrimoTentativoIlSegnapostoNonEApprossimato() {
        // Anche se prima lo era: l'indirizzo è cambiato e ora si trova col civico.
        Societa societa = campo("VIA DELLA CAMILLUCCIA 120", "ROMA").setPosizioneApprossimata(true);

        con(indirizzo -> Optional.of(new Geocoding.Coordinate(41.93, 12.44))).geocodifica(societa);

        assertThat(societa.getPosizioneApprossimata()).isNull();
    }

    @Test
    void unIndirizzoNonTrovatoNonESegnatoApprossimato() {
        Societa societa = campo("VIA INESISTENTE 1", "ROMA");

        con(indirizzo -> Optional.empty()).geocodifica(societa);

        // Manca del tutto: per il filtro basta l'assenza delle coordinate.
        assertThat(societa.getPosizioneApprossimata()).isNull();
    }

    @Test
    void segnaLaVersioneCheHaRinunciato() {
        Societa societa = campo("VIA INESISTENTE 1", "ROMA");

        con(indirizzo -> Optional.empty()).geocodifica(societa);

        assertThat(societa.getGeocodificaFallitaVersione()).isEqualTo(GeocodificaAutomatica.VERSIONE);
        assertThat(societa.getLat()).isNull();
        verify(repository).save(societa);
    }

    /** Un orologio che si sposta a mano. */
    private static final class OrologioFinto extends Clock {
        private Instant adesso = Instant.parse("2026-11-09T12:00:00Z");

        void avanza(Duration durata) {
            adesso = adesso.plus(durata);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return adesso;
        }
    }

    private static Geocoding nonRisponde() {
        return indirizzo -> {
            throw new GeocodingNonDisponibile("Nominatim non risponde", null);
        };
    }

    private GeocodificaAutomatica conOrologio(Geocoding geocoding, OrologioFinto orologio) {
        return new GeocodificaAutomatica(repository, geocoding, Duration.ZERO, orologio);
    }

    @Test
    void seNominatimNonRispondeLaSocietaNonVieneSegnataNeSalvata() {
        Societa societa = campo("VIA DELLA CAMILLUCCIA 120", "ROMA").setId("1");
        when(repository.primaDaGeocodificare(GeocodificaAutomatica.VERSIONE))
                .thenReturn(Optional.of(societa));

        conOrologio(nonRisponde(), new OrologioFinto()).geocodificaLaProssima();

        // Niente scritto: senza coordinate e senza rinuncia e' ancora in coda.
        assertThat(societa.getGeocodificaFallitaVersione()).isNull();
        assertThat(societa.getLat()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void seIlSecondoTentativoFallisceDiReteNonSiSalvaNiente() {
        // Il primo (con il civico) non trova niente, il secondo trova la rete giu':
        // non si puo' dire che l'indirizzo non esista.
        Societa societa = campo("VIA DELLA CAMILLUCCIA 120", "ROMA").setId("1");
        List<String> cercati = new ArrayList<>();
        Geocoding primoVuotoPoiRete =
                indirizzo -> {
                    cercati.add(indirizzo);
                    if (cercati.size() == 1) {
                        return Optional.empty();
                    }
                    throw new GeocodingNonDisponibile("Nominatim non risponde", null);
                };
        when(repository.primaDaGeocodificare(GeocodificaAutomatica.VERSIONE))
                .thenReturn(Optional.of(societa));

        conOrologio(primoVuotoPoiRete, new OrologioFinto()).geocodificaLaProssima();

        assertThat(cercati).hasSize(2);
        assertThat(societa.getGeocodificaFallitaVersione()).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    void dopoUnErroreDiReteLaRicercaSiFermaPoiRiprende() {
        OrologioFinto orologio = new OrologioFinto();
        Societa societa = campo("VIA DELLA CAMILLUCCIA 120", "ROMA").setId("1");
        when(repository.primaDaGeocodificare(GeocodificaAutomatica.VERSIONE))
                .thenReturn(Optional.of(societa));
        GeocodificaAutomatica geocodifica = conOrologio(nonRisponde(), orologio);

        geocodifica.geocodificaLaProssima();
        orologio.avanza(GeocodificaAutomatica.PAUSA_RETE.minusSeconds(1));
        geocodifica.geocodificaLaProssima();

        // Nella pausa non si chiede nemmeno al database.
        verify(repository, times(1)).primaDaGeocodificare(GeocodificaAutomatica.VERSIONE);

        orologio.avanza(Duration.ofSeconds(2));
        geocodifica.geocodificaLaProssima();

        // Passata la pausa la societa' e' rimandata: si cerca una diversa dalla prima.
        verify(repository).primaDaGeocodificare(eq(GeocodificaAutomatica.VERSIONE), eq(Set.of("1")));
    }

    @Test
    void laSocietaCheHaFallitoTornaInFondoEUnaltraPassaAvanti() {
        OrologioFinto orologio = new OrologioFinto();
        Societa primaInCoda = campo("VIA CHE FA FALLIRE LA RETE 1", "ROMA").setId("1");
        Societa seconda = campo("VIA DELLA CAMILLUCCIA 120", "ROMA").setId("2");
        when(repository.primaDaGeocodificare(GeocodificaAutomatica.VERSIONE))
                .thenReturn(Optional.of(primaInCoda));
        when(repository.primaDaGeocodificare(eq(GeocodificaAutomatica.VERSIONE), eq(Set.of("1"))))
                .thenReturn(Optional.of(seconda));
        // Fallisce per la prima, risponde per la seconda: il guasto e' di una sola richiesta.
        Geocoding soloLaPrimaFallisce =
                indirizzo -> {
                    if (indirizzo.contains("RETE")) {
                        throw new GeocodingNonDisponibile("Nominatim non risponde", null);
                    }
                    return Optional.of(new Geocoding.Coordinate(41.9, 12.5));
                };
        GeocodificaAutomatica geocodifica = conOrologio(soloLaPrimaFallisce, orologio);

        geocodifica.geocodificaLaProssima();
        orologio.avanza(GeocodificaAutomatica.PAUSA_RETE.plusSeconds(1));
        geocodifica.geocodificaLaProssima();

        assertThat(primaInCoda.getLat()).isNull();
        assertThat(primaInCoda.getGeocodificaFallitaVersione()).isNull();
        assertThat(seconda.getLat()).isEqualTo(41.9);
        verify(repository).save(seconda);
        verify(repository, never()).save(primaInCoda);
    }

    @Test
    void passatoIlRimandoLaSocietaTornaAvantiAllaCoda() {
        OrologioFinto orologio = new OrologioFinto();
        Societa societa = campo("VIA DELLA CAMILLUCCIA 120", "ROMA").setId("1");
        when(repository.primaDaGeocodificare(GeocodificaAutomatica.VERSIONE))
                .thenReturn(Optional.of(societa));
        GeocodificaAutomatica geocodifica = conOrologio(nonRisponde(), orologio);

        geocodifica.geocodificaLaProssima();
        orologio.avanza(GeocodificaAutomatica.RIMANDO.plusSeconds(1));
        geocodifica.geocodificaLaProssima();

        // Scaduto il rimando non c'e' piu' niente da escludere: la coda e' quella di sempre.
        verify(repository, times(2)).primaDaGeocodificare(GeocodificaAutomatica.VERSIONE);
    }

    @Test
    void chiedeAlDatabaseSoloICampiNonRinunciatiDaQuestaVersione() {
        when(repository.primaDaGeocodificare(anyInt())).thenReturn(Optional.empty());

        con(indirizzo -> Optional.empty()).geocodificaLaProssima();

        verify(repository).primaDaGeocodificare(GeocodificaAutomatica.VERSIONE);
        verify(repository, never()).save(any());
    }

    @Test
    void unErroreDelDatabaseNonFermaLoScheduler() {
        when(repository.primaDaGeocodificare(anyInt())).thenThrow(new IllegalStateException("mongo giù"));

        con(indirizzo -> Optional.empty()).geocodificaLaProssima();

        verify(repository, never()).save(any());
    }

    @Test
    void senzaCivicoNonRipeteLaStessaRicerca() {
        assertThat(GeocodificaAutomatica.tentativi(campo("VIA GALTELLI SNC", "FIUMICINO")))
                .containsExactly("VIA GALTELLI, FIUMICINO, Italia");
    }

    @Test
    void senzaLocalitaUsaIndirizzoEPaese() {
        assertThat(GeocodificaAutomatica.tentativi(campo("Via Roma", "")))
                .containsExactly("VIA ROMA, Italia");
    }

    /** Indirizzi presi dal Comunicato Ufficiale n. 20 del CRL Lazio. */
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "VIA GALTELLI SNC                | VIA GALTELLI",
                "VIA TIBERINA KM 11.00           | VIA TIBERINA",
                "VIA TIBERINA KM. 21,500         | VIA TIBERINA",
                "P.ZA MARTIRI DELLA LIBERTA' 27  | PIAZZA MARTIRI DELLA LIBERTA' 27",
                "VIALE  SPAGNA SNC               | VIALE SPAGNA",
                "S.P. SACROFANO-CASSIA           | STRADA PROVINCIALE SACROFANO-CASSIA",
                "VIA PANTANE 9/11                | VIA PANTANE 9",
                "LUNGOTEVERE DANTE 3/5           | LUNGOTEVERE DANTE 3",
                "VIA CASTIGLION FIORENTINO 40/5  | VIA CASTIGLION FIORENTINO 40",
                "LARGO MARTIRI DI VIA FANI SNC   | LARGO MARTIRI DI VIA FANI",
                "VIA DELLA CAMILLUCCIA 120       | VIA DELLA CAMILLUCCIA 120",
                "V.LE DELLE OLIMPIADI 4          | VIALE DELLE OLIMPIADI 4",
                "via della certosa 12            | VIA DELLA CERTOSA 12",
            })
    void ripulisceGliIndirizziDeiComunicati(String indirizzo, String atteso) {
        assertThat(GeocodificaAutomatica.pulisci(indirizzo)).isEqualTo(atteso);
    }

    @Test
    void ilCivicoInCodaSiTogliePerIlSecondoTentativo() {
        assertThat(GeocodificaAutomatica.tentativi(campo("VIA UMBERTO I, 3", "GUIDONIA MONTECELIO")))
                .containsExactly(
                        "VIA UMBERTO I, 3, GUIDONIA MONTECELIO, Italia",
                        "VIA UMBERTO I, GUIDONIA MONTECELIO, Italia");
    }
}
