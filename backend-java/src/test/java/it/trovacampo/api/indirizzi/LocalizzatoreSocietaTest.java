package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import it.trovacampo.api.service.Geocoding;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalizzatoreSocietaTest {

    private final FonteCampiLnd lnd = mock(FonteCampiLnd.class);
    private final FonteImpiantiOpenData openData = mock(FonteImpiantiOpenData.class);
    private final RicercaCampiOsm osm = mock(RicercaCampiOsm.class);
    private final Geocoding geocoding = mock(Geocoding.class);
    private final LocalizzatoreSocieta localizzatore = new LocalizzatoreSocieta(lnd, openData, osm, geocoding);

    private static final SocietaDaLocalizzare KOLBE =
            new SocietaDaLocalizzare("C.S. KOLBE PONTE MAMMOLO A.S.D", "ROMA", "97000000000", null);

    private static IndirizzoMigliore indirizzo(Fonte fonte, Double lat) {
        return new IndirizzoMigliore(
                "Via Casal de' Pazzi, 16", "ROMA", lat, lat, fonte, Confidenza.MEDIA, 1, "x", "x",
                fonte.attribuzione());
    }

    @Test
    void laPrimaFonteCheRispondeVince() {
        when(lnd.cerca(KOLBE)).thenReturn(Optional.of(indirizzo(Fonte.CAMPI_LND, 41.9)));

        assertThat(localizzatore.trova(KOLBE)).hasValueSatisfying(
                trovato -> assertThat(trovato.fonte()).isEqualTo(Fonte.CAMPI_LND));
        verifyNoInteractions(openData, osm, geocoding);
    }

    @Test
    void lIndirizzoOpenDataSenzaPosizioneSiGeocodifica() {
        when(lnd.cerca(KOLBE)).thenReturn(Optional.empty());
        when(openData.cerca(KOLBE)).thenReturn(Optional.of(indirizzo(Fonte.OPEN_DATA, null)));
        when(geocoding.geocodifica("VIA CASAL DE' PAZZI, 16, ROMA, Italia"))
                .thenReturn(Optional.of(new Geocoding.Coordinate(41.93, 12.54)));

        assertThat(localizzatore.trova(KOLBE)).hasValueSatisfying(trovato -> {
            assertThat(trovato.fonte()).isEqualTo(Fonte.OPEN_DATA);
            assertThat(trovato.lat()).isEqualTo(41.93);
            assertThat(trovato.attribuzione()).contains("CC0", "OpenStreetMap");
        });
        verifyNoInteractions(osm);
    }

    @Test
    void senzaReteNienteNominatim() {
        when(lnd.cerca(KOLBE)).thenReturn(Optional.empty());
        when(openData.cerca(KOLBE)).thenReturn(Optional.of(indirizzo(Fonte.OPEN_DATA, null)));

        assertThat(localizzatore.trova(KOLBE, LocalizzatoreSocieta.Opzioni.SENZA_RETE))
                .hasValueSatisfying(trovato -> assertThat(trovato.haCoordinate()).isFalse());
        verifyNoInteractions(osm, geocoding);
    }

    @Test
    void osmSoloComeUltimaRisorsa() {
        when(lnd.cerca(KOLBE)).thenReturn(Optional.empty());
        when(openData.cerca(KOLBE)).thenReturn(Optional.empty());
        when(osm.cerca(KOLBE)).thenReturn(Optional.of(indirizzo(Fonte.GEOCODING_OSM, 41.9)));

        assertThat(localizzatore.trova(KOLBE)).hasValueSatisfying(
                trovato -> assertThat(trovato.fonte()).isEqualTo(Fonte.GEOCODING_OSM));
        verify(geocoding, never()).geocodifica(anyString());
    }

    @Test
    void senzaNomeNienteRicerca() {
        assertThat(localizzatore.trova(new SocietaDaLocalizzare(" ", "ROMA"))).isEmpty();
        verify(lnd, never()).cerca(any());
    }
}
