package it.trovacampo.api.prospect;

import java.time.Instant;

/** Come è andato (o come sta andando) l'ultimo giro, da quando il backend è acceso. */
public record AvanzamentoImportazione(
        String stato,
        Instant iniziato,
        Instant finito,
        int pagine,
        int richieste,
        int lette,
        int nuove,
        int dettagli,
        int dettagliSaltati,
        String regioneCorrente,
        String errore,
        CollegamentoCampi.Esito collegamento) {

    static AvanzamentoImportazione mai() {
        return new AvanzamentoImportazione("mai", null, null, 0, 0, 0, 0, 0, 0, null, null, null);
    }
}
