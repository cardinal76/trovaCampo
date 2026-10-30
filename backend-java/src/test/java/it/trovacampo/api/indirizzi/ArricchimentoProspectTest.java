package it.trovacampo.api.indirizzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import it.trovacampo.api.indirizzi.LettoreProspect.SocietaProspect;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ArricchimentoProspectTest {

    private final LettoreProspect lettore = mock(LettoreProspect.class);
    private final LocalizzatoreSocieta localizzatore = mock(LocalizzatoreSocieta.class);
    private final IndirizziProspectRepository risultati = mock(IndirizziProspectRepository.class);
    private final ArricchimentoProspect arricchimento =
            new ArricchimentoProspect(
                    lettore, localizzatore, risultati, Runnable::run,
                    Clock.fixed(Instant.parse("2026-10-30T12:00:00Z"), ZoneOffset.UTC));

    private static final SocietaProspect ROMULEA = new SocietaProspect("80000000001", "A.S.D. ROMULEA", "ROMA", null);
    private static final SocietaProspect IGNOTA = new SocietaProspect("80000000002", "A.S.D. IGNOTA", "ARCE", null);

    private static final IndirizzoMigliore FARSALO =
            new IndirizzoMigliore("VIA FARSALO 21", "ROMA", 41.88, 12.50, Fonte.CAMPI_LND, Confidenza.ALTA, 1,
                    "SS ROMULEA", "ROMULEA", Fonte.CAMPI_LND.attribuzione());

    @Test
    void senzaProspectLoDiceENonFallisce() {
        when(lettore.esiste()).thenReturn(false);
        when(lettore.collection()).thenReturn("prospect");

        ArricchimentoProspect.Avvio avvio = arricchimento.avvia(new ArricchimentoProspect.Richiesta(true, false, null));

        assertThat(avvio.avviato()).isFalse();
        assertThat(avvio.messaggio()).contains("prospect", "non esiste ancora");
        assertThat(arricchimento.copertura().disponibile()).isFalse();
        verify(lettore, never()).societaDiCalcioDelLazio();
    }

    @Test
    void cercaTutteEScriveLEsitoAncheSeNonTrova() {
        when(lettore.esiste()).thenReturn(true);
        when(lettore.societaDiCalcioDelLazio())
                .thenReturn(new LettoreProspect.Lettura(List.of(ROMULEA, IGNOTA), true, "regione = Lazio"));
        when(risultati.findAllById(anyIterable())).thenReturn(List.of());
        when(localizzatore.trova(any(), any())).thenReturn(Optional.empty());
        when(localizzatore.trova(ROMULEA.daLocalizzare(), LocalizzatoreSocieta.Opzioni.SENZA_RETE))
                .thenReturn(Optional.of(FARSALO));

        ArricchimentoProspect.Avvio avvio =
                arricchimento.avvia(new ArricchimentoProspect.Richiesta(false, false, null));

        assertThat(avvio.avviato()).isTrue();
        ArgumentCaptor<IndirizzoProspect> salvati = ArgumentCaptor.forClass(IndirizzoProspect.class);
        verify(risultati, times(2)).save(salvati.capture());
        assertThat(salvati.getAllValues().get(0).fonte()).isEqualTo(Fonte.CAMPI_LND);
        assertThat(salvati.getAllValues().get(0).codiceFiscale()).isEqualTo("80000000001");
        assertThat(salvati.getAllValues().get(1).trovato()).isFalse();
        assertThat(arricchimento.stato().cercate()).isEqualTo(2);
        assertThat(arricchimento.stato().trovate()).isEqualTo(1);
        assertThat(arricchimento.stato().inCorso()).isFalse();
    }

    @Test
    void nonRicercaQuelleGiaTrovateMaRitentaConLaReteQuelleCercateSenza() {
        when(lettore.esiste()).thenReturn(true);
        when(lettore.societaDiCalcioDelLazio())
                .thenReturn(new LettoreProspect.Lettura(List.of(ROMULEA, IGNOTA), true, "regione = Lazio"));
        Instant prima = Instant.parse("2026-10-29T12:00:00Z");
        when(risultati.findAllById(anyIterable()))
                .thenReturn(List.of(
                        IndirizzoProspect.di(ROMULEA, FARSALO, false, prima),
                        IndirizzoProspect.di(IGNOTA, null, false, prima)));
        when(localizzatore.trova(any(), any())).thenReturn(Optional.empty());

        arricchimento.avvia(new ArricchimentoProspect.Richiesta(true, false, null));

        verify(localizzatore).trova(IGNOTA.daLocalizzare(), LocalizzatoreSocieta.Opzioni.COMPLETA);
        verify(localizzatore, never()).trova(ROMULEA.daLocalizzare(), LocalizzatoreSocieta.Opzioni.COMPLETA);
    }

    @Test
    void laCoperturaPerFonte() {
        when(lettore.esiste()).thenReturn(true);
        when(lettore.societaDiCalcioDelLazio())
                .thenReturn(new LettoreProspect.Lettura(List.of(ROMULEA, IGNOTA), true, "regione = Lazio"));
        Instant quando = Instant.parse("2026-10-29T12:00:00Z");
        when(risultati.findAllById(anyIterable()))
                .thenReturn(List.of(
                        IndirizzoProspect.di(ROMULEA, FARSALO, true, quando),
                        IndirizzoProspect.di(IGNOTA, null, true, quando)));

        ArricchimentoProspect.Copertura copertura = arricchimento.copertura();

        assertThat(copertura.totale()).isEqualTo(2);
        assertThat(copertura.cercate()).isEqualTo(2);
        assertThat(copertura.conIndirizzo()).isEqualTo(1);
        assertThat(copertura.percentuale()).isEqualTo(0.5);
        assertThat(copertura.perFonte()).containsEntry(Fonte.CAMPI_LND, 1).hasSize(1);
        assertThat(copertura.perConfidenza()).containsEntry(Confidenza.ALTA, 1);
        assertThat(copertura.obiettivo()).isEqualTo(0.70);
    }
}
