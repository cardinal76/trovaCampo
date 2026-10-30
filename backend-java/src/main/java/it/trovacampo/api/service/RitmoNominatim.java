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
 * ogni richiesta. Il conto lo tiene un {@link LimitatoreRichieste}, lo
 * stesso del Registro RASD.
 */
@Component
public class RitmoNominatim {

    private final LimitatoreRichieste limitatore;

    @Autowired
    public RitmoNominatim(@Value("${trovacampo.nominatim.intervallo:PT1.1S}") Duration intervallo) {
        this(new LimitatoreRichieste(intervallo));
    }

    /** Per i test, con un limitatore che non dorme davvero. */
    public RitmoNominatim(LimitatoreRichieste limitatore) {
        this.limitatore = limitatore;
    }

    /** Aspetta, se serve, che dall'ultima richiesta sia passato l'intervallo. */
    public void attendi() {
        try {
            limitatore.prima();
        } catch (InterruptedException eccezione) {
            Thread.currentThread().interrupt();
        }
    }
}
