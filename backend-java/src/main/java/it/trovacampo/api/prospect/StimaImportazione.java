package it.trovacampo.api.prospect;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Quanto manca, a stima: pagine di elenco, dettagli, e il tempo al ritmo configurato. */
public record StimaImportazione(long pagine, long dettagli, long richieste, long secondi, double giorni) {

    /** Società per regione quando il registro non ha ancora detto il totale: circa 100.000 / 20. */
    static final int PER_REGIONE = 5_000;

    /** Quanto manca alle regioni del giro, dai totali che il registro ha già detto. */
    static StimaImportazione di(
            OpzioniImportazione opzioni,
            List<StatoImportazioneProspect> salvati,
            RitmoRasd.Configurazione ritmo,
            int paginaDa) {
        Map<Integer, StatoImportazioneProspect> perId =
                salvati.stream()
                        .collect(Collectors.toMap(StatoImportazioneProspect::getRegioneId, Function.identity(), (a, b) -> a));
        long pagine = 0;
        long societa = 0;
        for (Integer id : opzioni.regioniDaFare()) {
            StatoImportazioneProspect s = perId.get(id);
            if (s != null && s.isCompletata()) {
                continue;
            }
            long totale = s != null && s.getTotale() != null ? s.getTotale() : PER_REGIONE;
            long restano = Math.max(0, totale - (s == null ? 0 : s.getProssimoStart()));
            societa += restano;
            pagine += (restano + paginaDa - 1) / paginaDa;
        }
        long dettagli = Math.round(societa * opzioni.dettagli().quota);
        long richieste = pagine + dettagli;
        Duration tempo = ritmo.stima(richieste);
        return new StimaImportazione(pagine, dettagli, richieste, tempo.toSeconds(), Math.round(tempo.toHours() / 2.4) / 10.0);
    }
}
