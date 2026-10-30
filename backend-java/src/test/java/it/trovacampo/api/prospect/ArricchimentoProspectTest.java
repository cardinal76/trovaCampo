package it.trovacampo.api.prospect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.indirizzi.Confidenza;
import it.trovacampo.api.indirizzi.FonteIndirizzo;
import it.trovacampo.api.indirizzi.IndirizzoMigliore;
import it.trovacampo.api.indirizzi.LocalizzatoreSocieta;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ArricchimentoProspectTest {

    private static final Instant ADESSO = Instant.parse("2026-10-31T10:00:00Z");

    private final ProspectRepository prospect = mock(ProspectRepository.class);
    private final LocalizzatoreSocieta localizzatore = mock(LocalizzatoreSocieta.class);
    private final ArricchimentoProspect arricchimento =
            new ArricchimentoProspect(prospect, localizzatore, Runnable::run, Clock.fixed(ADESSO, ZoneOffset.UTC));

    private static Prospect lazio(String cf, String denominazione) {
        return new Prospect().setCodiceFiscale(cf).setDenominazione(denominazione).setComune("Ronciglione")
                .setRegione("Lazio").setCalcio(true);
    }

    private static final IndirizzoMigliore BIGI =
            new IndirizzoMigliore(
                    "Via San Giovanni", "Ronciglione", 42.28, 12.21, FonteIndirizzo.GEOCODIFICA, Confidenza.BASSA,
                    0.2, "Campo Sportivo Marco Bigi", "Campo Sportivo Marco Bigi", "osm-paese");

    @Test
    void senzaProspectLoDiceENonFallisce() {
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of());

        ArricchimentoProspect.Avvio avvio = arricchimento.avvia(new ArricchimentoProspect.Richiesta(false, null));

        assertThat(avvio.avviato()).isFalse();
        assertThat(avvio.messaggio()).isEqualTo(ArricchimentoProspect.NESSUN_PROSPECT);
        verify(localizzatore, never()).trova(any(), any());
    }

    @Test
    void cercaConLaReteSoloChiNonHaIndirizzoOPosizione() {
        Prospect senza = lazio("CF1", "A.S.D. VICUS");
        Prospect conCampo = lazio("CF2", "A.S.D. ALTRA")
                .setFonteIndirizzo(FonteIndirizzo.CAMPO_TROVACAMPO).setLat(41.0).setLng(12.0);
        Prospect giaCercato = lazio("CF3", "A.S.D. TERZA").setGeocodificaTentata(ADESSO.minusSeconds(60));
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(senza, conCampo, giaCercato));
        when(localizzatore.trova(CollegamentoCampi.daLocalizzare(senza), LocalizzatoreSocieta.Opzioni.COMPLETA))
                .thenReturn(Optional.of(BIGI));

        ArricchimentoProspect.Avvio avvio = arricchimento.avvia(new ArricchimentoProspect.Richiesta(false, null));

        assertThat(avvio.avviato()).isTrue();
        assertThat(senza.getFonteIndirizzo()).isEqualTo(FonteIndirizzo.GEOCODIFICA);
        assertThat(senza.getConfidenzaIndirizzo()).isEqualTo(Confidenza.BASSA);
        assertThat(senza.getGeocodificaTentata()).isEqualTo(ADESSO);
        verify(prospect).save(senza);
        verify(localizzatore, never()).trova(CollegamentoCampi.daLocalizzare(conCampo), LocalizzatoreSocieta.Opzioni.COMPLETA);
        verify(localizzatore, never()).trova(CollegamentoCampi.daLocalizzare(giaCercato), LocalizzatoreSocieta.Opzioni.COMPLETA);
        assertThat(arricchimento.stato().cercate()).isEqualTo(1);
        assertThat(arricchimento.stato().trovate()).isEqualTo(1);
        assertThat(arricchimento.stato().inCorso()).isFalse();
    }

    @Test
    void conRitentaAncheQuelliGiaCercatiEConIlLimite() {
        Prospect uno = lazio("CF1", "A").setGeocodificaTentata(ADESSO.minusSeconds(60));
        Prospect due = lazio("CF2", "B");
        when(prospect.findByRegioneAndCalcioTrue("Lazio")).thenReturn(List.of(uno, due));
        when(localizzatore.trova(any(), any())).thenReturn(Optional.empty());

        arricchimento.avvia(new ArricchimentoProspect.Richiesta(true, 1));

        assertThat(arricchimento.stato().daCercare()).isEqualTo(1);
        verify(prospect).save(uno);
        verify(prospect, never()).save(due);
    }
}
