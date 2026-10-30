package it.trovacampo.api.service;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Il ritmo delle richieste a Nominatim, uno per tutto il backend.
 *
 * <p>La usage policy di Nominatim chiede al massimo una richiesta al secondo
 * per applicazione, non per pezzo di codice: la geocodifica automatica dei
 * campi e la ricerca degli indirizzi delle società
 * ({@code it.trovacampo.api.indirizzi}) possono girare insieme, e insieme
 * devono restare sotto il limite. Chi chiama Nominatim passa da qui prima di
 * ogni richiesta.
 */
@Component
public class RitmoNominatim {

    private final long intervalloNanos;
    private long ultima = Long.MIN_VALUE;

    @Autowired
    public RitmoNominatim(@Value("${trovacampo.nominatim.intervallo:PT1.1S}") Duration intervallo) {
        this.intervalloNanos = intervallo.toNanos();
    }

    /** Aspetta, se serve, che dall'ultima richiesta sia passato l'intervallo. */
    public synchronized void attendi() {
        if (ultima != Long.MIN_VALUE) {
            long attesa = ultima + intervalloNanos - System.nanoTime();
            if (attesa > 0) {
                try {
                    Thread.sleep(Duration.ofNanos(attesa));
                } catch (InterruptedException eccezione) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        ultima = System.nanoTime();
    }
}
