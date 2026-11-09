package it.trovacampo.api.prospect;

import java.time.Instant;
import java.util.List;

/**
 * Lo stato per la pagina admin: fase (in corso, in pausa per la finestra
 * o per il tetto, bloccata, fermata, completata, interrotta da un
 * riavvio), regione corrente, richieste di oggi, motivo di un blocco,
 * stima del tempo rimanente e punto di ripresa di ogni regione.
 */
public record StatoImportazione(
        boolean inCorso,
        String fase,
        Instant pausaFinoA,
        String regioneCorrente,
        int richiesteOggi,
        boolean bloccata,
        String motivo,
        Instant bloccataIl,
        boolean riprendibile,
        OpzioniImportazione opzioni,
        RitmoImportazione ritmo,
        StimaImportazione stima,
        AvanzamentoImportazione ultimo,
        List<StatoImportazioneProspect> regioni) {}
