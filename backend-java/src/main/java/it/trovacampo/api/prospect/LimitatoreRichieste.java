package it.trovacampo.api.prospect;

import java.time.Duration;
import java.util.function.LongSupplier;

/**
 * Tiene almeno {@code intervallo} fra l'inizio di una richiesta al registro e
 * quello della successiva: la card chiede al massimo una richiesta al
 * secondo, e sotto il secondo non si scende nemmeno configurandolo.
 *
 * <p>Orologio e attesa si passano da fuori perché i test li controllino
 * senza dormire davvero.
 */
public class LimitatoreRichieste {

    public static final Duration MINIMO = Duration.ofSeconds(1);

    /** Come si aspetta: {@link Thread#sleep(long)}, o un finto nei test. */
    @FunctionalInterface
    public interface Attesa {
        void dormi(long millisecondi) throws InterruptedException;
    }

    private final long intervalloNanos;
    private final LongSupplier orologioNanos;
    private final Attesa attesa;
    private long ultima = Long.MIN_VALUE;

    public LimitatoreRichieste(Duration intervallo) {
        this(intervallo, System::nanoTime, Thread::sleep);
    }

    public LimitatoreRichieste(Duration intervallo, LongSupplier orologioNanos, Attesa attesa) {
        Duration effettivo = intervallo.compareTo(MINIMO) < 0 ? MINIMO : intervallo;
        this.intervalloNanos = effettivo.toNanos();
        this.orologioNanos = orologioNanos;
        this.attesa = attesa;
    }

    public long intervalloMillis() {
        return Duration.ofNanos(intervalloNanos).toMillis();
    }

    /** Aspetta quanto serve, poi segna l'inizio di una nuova richiesta. */
    public synchronized void prima() throws InterruptedException {
        long adesso = orologioNanos.getAsLong();
        if (ultima != Long.MIN_VALUE) {
            long mancano = ultima + intervalloNanos - adesso;
            if (mancano > 0) {
                // Arrotondato in su: mezzo millisecondo in meno sarebbe già sotto il limite.
                attesa.dormi((mancano + 999_999) / 1_000_000);
                adesso = orologioNanos.getAsLong();
            }
        }
        ultima = adesso;
    }
}
