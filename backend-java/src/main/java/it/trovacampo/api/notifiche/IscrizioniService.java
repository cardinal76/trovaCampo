package it.trovacampo.api.notifiche;

import it.trovacampo.api.web.DatiNonValidiException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Service;

/**
 * Iscrizioni alle notifiche: si salvano, si aggiornano, si cancellano.
 *
 * <p>L'endpoint deve essere di un servizio push conosciuto. Il backend ci
 * manda richieste POST: senza questo controllo chiunque potrebbe fargli
 * chiamare un indirizzo a sua scelta, anche dentro la rete di Docker.
 */
@Service
public class IscrizioniService {

    public static final int MASSIMO_ENDPOINT = 1024;
    public static final int MASSIMO_SQUADRE = 30;
    public static final int MASSIMO_RAGGIO_KM = 50;
    static final int RAGGIO_PREDEFINITO_KM = 10;
    /**
     * Un'iscrizione che il sito non rinfresca da un anno (lo fa a ogni
     * apertura) è di un telefono che non c'è più, e con lei se ne va la
     * posizione salvata.
     */
    static final Duration CONSERVAZIONE = Duration.ofDays(365);

    private final IscrizioniRepository repository;
    private final MongoTemplate mongo;
    private final List<String> serviziPush;
    private final Clock orologio;
    private final AtomicBoolean indiceCreato = new AtomicBoolean();

    @Autowired
    public IscrizioniService(
            IscrizioniRepository repository,
            MongoTemplate mongo,
            @Value("${trovacampo.notifiche.servizi-push}") List<String> serviziPush) {
        this(repository, mongo, serviziPush, Clock.systemUTC());
    }

    IscrizioniService(
            IscrizioniRepository repository, MongoTemplate mongo, List<String> serviziPush, Clock orologio) {
        this.repository = repository;
        this.mongo = mongo;
        this.serviziPush = serviziPush.stream().map(s -> s.strip().toLowerCase(Locale.ROOT)).toList();
        this.orologio = orologio;
    }

    /** Crea o sostituisce l'iscrizione di quel browser. */
    public Iscrizione salva(IscrizioneRequest richiesta) {
        controllaEndpoint(richiesta.endpoint());
        if (!CrittografiaWebPush.chiavePubblicaValida(richiesta.keys().p256dh())) {
            throw new DatiNonValidiException("Chiave p256dh non valida");
        }
        if (!CrittografiaWebPush.authValido(richiesta.keys().auth())) {
            throw new DatiNonValidiException("Chiave auth non valida");
        }
        if (richiesta.avvisoVicino() && richiesta.posizione() == null) {
            throw new DatiNonValidiException("Per le partite vicine serve la posizione");
        }

        Instant adesso = orologio.instant();
        // La posizione solo per chi ha acceso l'avviso che la usa, e senza
        // decimali inutili: un centinaio di metri basta a dire "a 2,3 km".
        boolean conPosizione = richiesta.avvisoVicino();
        Iscrizione iscrizione =
                new Iscrizione(
                        Iscrizione.idDi(richiesta.endpoint()),
                        richiesta.endpoint(),
                        richiesta.keys().p256dh(),
                        richiesta.keys().auth(),
                        richiesta.squadre().stream().distinct().toList(),
                        richiesta.avvisoSquadre(),
                        richiesta.avvisoVicino(),
                        conPosizione ? arrotonda(richiesta.posizione().lat()) : null,
                        conPosizione ? arrotonda(richiesta.posizione().lng()) : null,
                        conPosizione
                                ? (richiesta.raggioKm() == null ? RAGGIO_PREDEFINITO_KM : richiesta.raggioKm())
                                : null,
                        conPosizione ? adesso : null,
                        adesso);
        assicuraIndice();
        return repository.save(iscrizione);
    }

    /** Senza errori se non c'era: il telefono la cancella comunque dal suo lato. */
    public void cancella(String endpoint) {
        repository.deleteById(Iscrizione.idDi(endpoint));
    }

    private void controllaEndpoint(String endpoint) {
        URI indirizzo;
        try {
            indirizzo = new URI(endpoint);
        } catch (URISyntaxException eccezione) {
            throw new DatiNonValidiException("Endpoint non valido");
        }
        String host = indirizzo.getHost() == null ? "" : indirizzo.getHost().toLowerCase(Locale.ROOT);
        if (!"https".equals(indirizzo.getScheme()) || indirizzo.getUserInfo() != null || indirizzo.getPort() != -1) {
            throw new DatiNonValidiException("Endpoint non valido");
        }
        boolean conosciuto =
                serviziPush.stream().anyMatch(servizio -> host.equals(servizio) || host.endsWith("." + servizio));
        if (!conosciuto) {
            throw new DatiNonValidiException("Servizio push non riconosciuto");
        }
    }

    private static double arrotonda(double gradi) {
        return Math.round(gradi * 1000) / 1000.0;
    }

    /** Al primo salvataggio e non all'avvio, come il registro degli invii. */
    private void assicuraIndice() {
        if (!indiceCreato.get()) {
            mongo.indexOps(Iscrizione.class)
                    .createIndex(new Index().on("aggiornataIl", Sort.Direction.ASC).expire(CONSERVAZIONE));
            indiceCreato.set(true);
        }
    }
}
