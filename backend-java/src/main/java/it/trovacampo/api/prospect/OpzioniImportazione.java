package it.trovacampo.api.prospect;

import java.util.List;

/**
 * Cosa importare: le regioni (vuoto = tutte, Lazio per primo), quante
 * pagine al massimo in questo giro (nullo = fino in fondo; utile per una
 * prova), se ripartire da zero e per chi leggere il dettaglio.
 */
public record OpzioniImportazione(
        List<Integer> regioni, Integer maxPagine, boolean ricomincia, AmbitoDettagli dettagli) {

    public OpzioniImportazione {
        regioni = regioni == null ? List.of() : List.copyOf(regioni);
        dettagli = dettagli == null ? AmbitoDettagli.NESSUNO : dettagli;
    }

    public static OpzioniImportazione tutto() {
        return new OpzioniImportazione(List.of(), null, false, AmbitoDettagli.CALCIO);
    }

    List<Integer> regioniDaFare() {
        return regioni.isEmpty() ? List.copyOf(RegistroRasd.REGIONI.keySet()) : regioni;
    }
}
