package it.trovacampo.api.notifiche;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Il giro del {@link PianificatoreNotifiche} ogni cinque minuti, allo scoccare
 * dei cinque (15:00, 15:05…): le partite cominciano quasi sempre all'ora o
 * alla mezza, e l'avviso arriva così un'ora o mezz'ora esatta prima.
 *
 * <p>Si spegne con {@code trovacampo.notifiche.attive=false}: le iscrizioni
 * si raccolgono lo stesso, ma non parte niente.
 */
@Component
@ConditionalOnProperty(name = "trovacampo.notifiche.attive", havingValue = "true")
public class NotificheProgrammate {

    private static final Logger log = LoggerFactory.getLogger(NotificheProgrammate.class);

    private final PianificatoreNotifiche pianificatore;

    public NotificheProgrammate(PianificatoreNotifiche pianificatore) {
        this.pianificatore = pianificatore;
    }

    @Scheduled(cron = "0 */5 * * * *", zone = "Europe/Rome")
    public void giro() {
        try {
            pianificatore.giro();
        } catch (RuntimeException eccezione) {
            // Mongo o presenze giù per un momento: si riprova fra cinque minuti.
            log.warn("Giro delle notifiche non riuscito: {}", eccezione.toString());
        }
    }
}
