package it.trovacampo.api.prospect;

import it.trovacampo.api.prospect.RitmoRasd;
/** Il ritmo configurato, come lo vede la pagina. */
public record RitmoImportazione(
        long pausaMs, long jitterMs, String finestra, String fuso, int tettoGiornaliero, long richiestePerGiorno) {

    static RitmoImportazione di(RitmoRasd.Configurazione c) {
        return new RitmoImportazione(
                c.pausa().toMillis(), c.jitter().toMillis(), c.finestra(), c.fuso().getId(),
                c.tettoGiornaliero(), c.richiestePerGiorno());
    }
}
