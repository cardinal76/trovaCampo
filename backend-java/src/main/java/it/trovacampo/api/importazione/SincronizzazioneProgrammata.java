package it.trovacampo.api.importazione;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Tre volte al giorno, mezz'ora dopo che presenze ha letto i comunicati
 * nuovi (7:15, 13:15, 19:15): i campi comparsi in un programma gare arrivano
 * su TrovaCampo senza che nessuno carichi niente.
 */
@Component
@ConditionalOnProperty(name = "trovacampo.anagrafica.sincronizzazione", havingValue = "true")
public class SincronizzazioneProgrammata {

    private static final Logger log = LoggerFactory.getLogger(SincronizzazioneProgrammata.class);

    private final SincronizzazioneAnagrafica sincronizzazione;

    public SincronizzazioneProgrammata(SincronizzazioneAnagrafica sincronizzazione) {
        this.sincronizzazione = sincronizzazione;
    }

    @Scheduled(cron = "0 45 7,13,19 * * *", zone = "Europe/Rome")
    public void sincronizza() {
        try {
            sincronizzazione.sincronizza(false);
        } catch (RuntimeException eccezione) {
            // presenze giù per un aggiornamento: si riprova al giro dopo.
            log.warn("Sincronizzazione con l'anagrafica di presenze non riuscita: {}", eccezione.toString());
        }
    }
}
