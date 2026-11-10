package it.trovacampo.api.segnalazioni;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Salva le richieste. Il form e' pubblico: per non riempire Mongo se qualcuno
 * lo martella, oltre un tetto orario (per tutti insieme, perche' dietro Caddy
 * non si distinguono gli indirizzi) si risponde di riprovare piu' tardi.
 */
@Service
public class SegnalazioniService {

    static final int MASSIMO_ALL_ORA = 100;

    private final SegnalazioniRepository repository;
    private final Clock orologio;

    @Autowired
    public SegnalazioniService(SegnalazioniRepository repository) {
        this(repository, Clock.systemUTC());
    }

    SegnalazioniService(SegnalazioniRepository repository, Clock orologio) {
        this.repository = repository;
        this.orologio = orologio;
    }

    /** @return {@code false} se il tetto orario e' pieno */
    public boolean registra(SegnalazioneRequest r) {
        Instant adesso = orologio.instant();
        if (repository.countByRicevutaIlAfter(adesso.minus(Duration.ofHours(1))) >= MASSIMO_ALL_ORA) {
            return false;
        }
        repository.save(
                new Segnalazione(
                        null,
                        r.tipo(),
                        vuotoANull(r.societaId()),
                        vuotoANull(r.attuale()),
                        r.corretto().trim(),
                        vuotoANull(r.contatto()),
                        adesso,
                        false));
        return true;
    }

    private static String vuotoANull(String testo) {
        return testo == null || testo.isBlank() ? null : testo.trim();
    }
}
