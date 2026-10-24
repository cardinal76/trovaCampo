package it.trovacampo.api.notifiche;

import java.time.Duration;
import java.time.Instant;

/**
 * I due avvisi, con l'anticipo e quando partono davvero: lo stesso conto per
 * il {@link PianificatoreNotifiche}, che li manda, e per la pagina Prossime
 * partite ({@link ProssimePartite}), che dice quando arriveranno.
 *
 * <ul>
 *   <li>{@link #SQUADRA}: un'ora prima delle partite di una squadra seguita,
 *       in casa o fuori;
 *   <li>{@link #VICINO}: mezz'ora prima delle partite su un campo entro il
 *       raggio dalla posizione salvata.
 * </ul>
 */
public enum TipoAvviso {
    SQUADRA(Duration.ofMinutes(60), "Tra un'ora"),
    VICINO(Duration.ofMinutes(30), "Tra mezz'ora");

    /** Ogni quanto gira il pianificatore: vedi {@link NotificheProgrammate}. */
    public static final Duration PASSO = Duration.ofMinutes(5);

    /** Quanto prima dell'inizio della partita: è anche quanto il servizio push la tiene. */
    final Duration anticipo;
    /** L'inizio del titolo della notifica. */
    final String quando;

    TipoAvviso(Duration anticipo, String quando) {
        this.anticipo = anticipo;
        this.quando = quando;
    }

    /**
     * [anticipo - passo, anticipo + passo): mezza finestra prima e mezza dopo.
     * È larga due passi, così ogni partita ci cade in due giri di fila e un
     * giro saltato non fa perdere l'avviso.
     */
    boolean contiene(Duration mancano) {
        return mancano.compareTo(anticipo.minus(PASSO)) >= 0 && mancano.compareTo(anticipo.plus(PASSO)) < 0;
    }

    /**
     * Il giro che manda l'avviso di una partita che comincia a {@code inizio}:
     * il primo, allo scoccare dei cinque minuti, in cui la partita entra nella
     * finestra di {@link #contiene}, cioè il primo dopo
     * {@code inizio - anticipo - passo}. Per una partita alle 15:00 è il giro
     * delle 14:00 (un'ora prima), per una alle 15:02 anche (mancano 62').
     */
    public Instant previsto(Instant inizio) {
        long passo = PASSO.toSeconds();
        long soglia = inizio.minus(anticipo).minus(PASSO).getEpochSecond();
        return Instant.ofEpochSecond((Math.floorDiv(soglia, passo) + 1) * passo);
    }
}
