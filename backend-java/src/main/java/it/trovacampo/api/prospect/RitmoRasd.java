package it.trovacampo.api.prospect;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;
import java.util.function.LongUnaryOperator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Il ritmo dell'importazione dal Registro RASD: la priorità è non farsi
 * bloccare, non la velocità.
 *
 * <ul>
 *   <li><b>pausa</b> fra una richiesta e l'altra, più un <b>jitter</b> casuale
 *       (di default 3-4 secondi), mai sotto il secondo;
 *   <li><b>finestra oraria</b> opzionale, per esempio 22:00-07:00 ora
 *       italiana: fuori finestra l'importazione si mette in pausa da sola e
 *       riparte all'apertura, senza perdere la posizione;
 *   <li><b>tetto giornaliero</b> di richieste (giorno di calendario, ora
 *       italiana): raggiunto, si riprende a mezzanotte.
 * </ul>
 *
 * <p>Orologio, attesa e numeri casuali si passano da fuori perché i test li
 * controllino senza dormire davvero. Le attese lunghe si fanno a pezzi di
 * {@link #PASSO}, così un «Ferma» arriva in fretta anche di giorno.
 */
@Component
public class RitmoRasd {

    static final Duration MINIMO = Duration.ofSeconds(1);
    static final Duration PASSO = Duration.ofMinutes(1);
    /** Quanto ci mette in media il registro a rispondere, per la stima. */
    static final Duration RISPOSTA_MEDIA = Duration.ofSeconds(1);

    /** Come si aspetta: {@link Thread#sleep(long)}, o un finto nei test. */
    @FunctionalInterface
    public interface Attesa {
        void dormi(long millisecondi) throws InterruptedException;
    }

    public enum Motivo {
        FINESTRA,
        TETTO
    }

    /** Chi vuole sapere delle pause: la pagina admin le mostra. */
    public interface Ascoltatore {
        void inPausa(Motivo motivo, Instant finoA);

        void inCorso();
    }

    /**
     * @param inizio e {@code fine} nulli: nessuna finestra, si lavora a ogni ora
     * @param tettoGiornaliero 0: nessun tetto
     */
    public record Configurazione(
            Duration pausa, Duration jitter, LocalTime inizio, LocalTime fine, ZoneId fuso, int tettoGiornaliero) {

        public Configurazione {
            pausa = pausa == null || pausa.compareTo(MINIMO) < 0 ? MINIMO : pausa;
            jitter = jitter == null || jitter.isNegative() ? Duration.ZERO : jitter;
            fuso = fuso == null ? ZoneId.of("Europe/Rome") : fuso;
            if (inizio == null || fine == null || inizio.equals(fine)) {
                inizio = null;
                fine = null;
            }
            tettoGiornaliero = Math.max(0, tettoGiornaliero);
        }

        public boolean conFinestra() {
            return inizio != null;
        }

        /** «22:00-07:00», o vuoto se si lavora a ogni ora. */
        public String finestra() {
            return conFinestra() ? inizio + "-" + fine : "";
        }

        /** «22:00-07:00», «22-7» o vuoto. */
        static Configurazione di(long pausaMs, long jitterMs, String finestra, String fuso, int tetto) {
            LocalTime inizio = null;
            LocalTime fine = null;
            if (finestra != null && !finestra.isBlank()) {
                String[] parti = finestra.trim().split("-");
                if (parti.length != 2) {
                    throw new IllegalArgumentException("Finestra oraria non valida: «" + finestra + "», serve per esempio 22:00-07:00");
                }
                inizio = ora(parti[0]);
                fine = ora(parti[1]);
            }
            return new Configurazione(
                    Duration.ofMillis(pausaMs),
                    Duration.ofMillis(jitterMs),
                    inizio,
                    fine,
                    fuso == null || fuso.isBlank() ? null : ZoneId.of(fuso),
                    tetto);
        }

        private static LocalTime ora(String testo) {
            String t = testo.trim();
            return t.contains(":") ? LocalTime.parse(t.length() == 4 ? "0" + t : t) : LocalTime.of(Integer.parseInt(t), 0);
        }

        /** Durata media di una richiesta, pausa compresa. */
        Duration perRichiesta() {
            return pausa.plus(jitter.dividedBy(2)).plus(RISPOSTA_MEDIA);
        }

        Duration aperta() {
            if (!conFinestra()) {
                return Duration.ofDays(1);
            }
            Duration d = Duration.between(inizio, fine);
            return d.isNegative() ? d.plusDays(1) : d;
        }

        /** Quante richieste ci stanno in un giorno, fra finestra e tetto. */
        long richiestePerGiorno() {
            long perFinestra = aperta().toMillis() / perRichiesta().toMillis();
            return tettoGiornaliero > 0 ? Math.min(tettoGiornaliero, perFinestra) : perFinestra;
        }

        /** Quanto tempo, a orologio, per fare tante richieste a questo ritmo. */
        Duration stima(long richieste) {
            if (richieste <= 0) {
                return Duration.ZERO;
            }
            long perGiorno = Math.max(1, richiestePerGiorno());
            long continue_ = Duration.ofDays(1).toMillis() / perRichiesta().toMillis();
            if (perGiorno >= continue_) {
                return perRichiesta().multipliedBy(richieste);
            }
            return Duration.ofMillis(Math.round(richieste * (double) Duration.ofDays(1).toMillis() / perGiorno));
        }
    }

    private final Configurazione configurazione;
    private final Clock orologio;
    private final Attesa attesa;
    private final LongUnaryOperator caso;

    private LocalDate giorno;
    private int richiesteGiorno;
    private boolean primaFatta;

    @Autowired
    public RitmoRasd(
            @Value("${trovacampo.prospect.rasd.ritmo.pausa-ms:3000}") long pausaMs,
            @Value("${trovacampo.prospect.rasd.ritmo.jitter-ms:1000}") long jitterMs,
            @Value("${trovacampo.prospect.rasd.ritmo.finestra:22:00-07:00}") String finestra,
            @Value("${trovacampo.prospect.rasd.ritmo.fuso:Europe/Rome}") String fuso,
            @Value("${trovacampo.prospect.rasd.ritmo.tetto-giornaliero:7000}") int tetto) {
        this(
                Configurazione.di(pausaMs, jitterMs, finestra, fuso, tetto),
                Clock.systemUTC(),
                Thread::sleep,
                limite -> limite <= 0 ? 0 : ThreadLocalRandom.current().nextLong(limite + 1));
    }

    /** Per i test: orologio, attesa e caso finti. {@code caso} riceve il jitter massimo in ms. */
    RitmoRasd(Configurazione configurazione, Clock orologio, Attesa attesa, LongUnaryOperator caso) {
        this.configurazione = configurazione;
        this.orologio = orologio;
        this.attesa = attesa;
        this.caso = caso;
    }

    public Configurazione configurazione() {
        return configurazione;
    }

    public Clock orologio() {
        return orologio;
    }

    /** Il contatore di oggi letto da Mongo, perché un riavvio non lo azzeri. */
    synchronized void ripristina(LocalDate giorno, int richieste) {
        this.giorno = giorno;
        this.richiesteGiorno = richieste;
    }

    /** Un nuovo giro: la prima richiesta non aspetta la pausa. */
    synchronized void nuovoGiro() {
        primaFatta = false;
    }

    public synchronized LocalDate giorno() {
        allinea(orologio.instant());
        return giorno;
    }

    public synchronized int richiesteOggi() {
        allinea(orologio.instant());
        return richiesteGiorno;
    }

    private void allinea(Instant adesso) {
        LocalDate oggi = adesso.atZone(configurazione.fuso()).toLocalDate();
        if (!oggi.equals(giorno)) {
            giorno = oggi;
            richiesteGiorno = 0;
        }
    }

    boolean inFinestra(Instant adesso) {
        if (!configurazione.conFinestra()) {
            return true;
        }
        LocalTime ora = adesso.atZone(configurazione.fuso()).toLocalTime();
        LocalTime inizio = configurazione.inizio();
        LocalTime fine = configurazione.fine();
        if (inizio.isBefore(fine)) {
            return !ora.isBefore(inizio) && ora.isBefore(fine);
        }
        // A cavallo della mezzanotte, come 22:00-07:00.
        return !ora.isBefore(inizio) || ora.isBefore(fine);
    }

    Instant prossimaApertura(Instant adesso) {
        ZonedDateTime qui = adesso.atZone(configurazione.fuso());
        ZonedDateTime oggi = qui.toLocalDate().atTime(configurazione.inizio()).atZone(configurazione.fuso());
        return (oggi.isAfter(qui) ? oggi : oggi.plusDays(1)).toInstant();
    }

    private Instant domani(Instant adesso) {
        return adesso.atZone(configurazione.fuso()).toLocalDate().plusDays(1).atStartOfDay(configurazione.fuso()).toInstant();
    }

    /**
     * Aspetta il momento di fare la prossima richiesta e la conta. Falso se
     * nel frattempo è arrivato un «Ferma»: la richiesta non si fa.
     */
    public boolean prima(BooleanSupplier fermati, Ascoltatore ascoltatore) throws InterruptedException {
        // Prima la pausa, poi la finestra e il tetto: la pausa può far uscire dalla finestra.
        if (primaFatta) {
            long pausa = configurazione.pausa().toMillis() + caso.applyAsLong(configurazione.jitter().toMillis());
            if (!dormi(pausa, fermati)) {
                return false;
            }
        }
        boolean inPausa = false;
        while (true) {
            if (fermati.getAsBoolean()) {
                return false;
            }
            Instant adesso = orologio.instant();
            Motivo motivo = null;
            Instant finoA = null;
            synchronized (this) {
                allinea(adesso);
                if (configurazione.tettoGiornaliero() > 0 && richiesteGiorno >= configurazione.tettoGiornaliero()) {
                    motivo = Motivo.TETTO;
                    finoA = domani(adesso);
                } else if (!inFinestra(adesso)) {
                    motivo = Motivo.FINESTRA;
                    finoA = prossimaApertura(adesso);
                }
            }
            if (motivo == null) {
                break;
            }
            ascoltatore.inPausa(motivo, finoA);
            inPausa = true;
            long mancano = Math.max(1, Duration.between(adesso, finoA).toMillis());
            attesa.dormi(Math.min(mancano, PASSO.toMillis()));
        }
        if (inPausa) {
            ascoltatore.inCorso();
        }
        synchronized (this) {
            allinea(orologio.instant());
            richiesteGiorno++;
            primaFatta = true;
        }
        return true;
    }

    private boolean dormi(long millisecondi, BooleanSupplier fermati) throws InterruptedException {
        long restano = millisecondi;
        while (restano > 0) {
            if (fermati.getAsBoolean()) {
                return false;
            }
            long pezzo = Math.min(restano, PASSO.toMillis());
            attesa.dormi(pezzo);
            restano -= pezzo;
        }
        return !fermati.getAsBoolean();
    }
}
