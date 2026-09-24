package it.trovacampo.api.importazione;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.service.Geocoding;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Dà le coordinate alle società che non le hanno, una alla volta.
 *
 * <p>Un file importato può portare migliaia di righe senza latitudine e
 * longitudine, e Nominatim ne accetta al massimo una al secondo:
 * geocodificarle durante il caricamento vorrebbe dire una richiesta HTTP che
 * dura ore. Qui invece il caricamento finisce subito e le coordinate arrivano
 * col tempo, al ritmo che la usage policy di Nominatim consente.
 *
 * <p>Gli indirizzi dei comunicati LND non sono scritti per un motore di
 * ricerca: "VIA GALTELLI SNC", "VIA TIBERINA KM 11.00", "P.ZA MARTIRI DELLA
 * LIBERTA' 27". Nominatim cerca tutte le parole, e basta un "SNC" (senza
 * numero civico) perché non trovi niente. Per questo l'indirizzo si ripulisce
 * prima, e se non basta si riprova senza il civico: meglio il segnaposto
 * sulla via giusta che nessun segnaposto. Un segnaposto trovato così resta
 * segnato come {@linkplain Societa#getPosizioneApprossimata() approssimato},
 * perché chi amministra possa ritrovarlo e correggerlo.
 *
 * <p>Un indirizzo non riconosciuto viene segnato con la versione di questa
 * ricerca e non viene ritentato, altrimenti la coda resterebbe per sempre
 * bloccata sulla stessa società. Quando la ricerca migliora, si alza
 * {@link #VERSIONE} e i campi rinunciati tornano in coda da soli.
 */
@Component
@ConditionalOnProperty(name = "trovacampo.geocodifica.automatica", havingValue = "true")
public class GeocodificaAutomatica {

    /**
     * 1: l'indirizzo così com'era. 2: indirizzo ripulito e secondo tentativo
     * senza civico.
     */
    static final int VERSIONE = 2;

    private static final Logger log = LoggerFactory.getLogger(GeocodificaAutomatica.class);

    /** Abbreviazioni dei comunicati, sciolte perché Nominatim le capisca. */
    private static final List<Sostituzione> ABBREVIAZIONI =
            List.of(
                    new Sostituzione("\\bP\\.?\\s?ZZ?A\\b\\.?", "PIAZZA"),
                    new Sostituzione("\\bP\\.?\\s?LE\\b\\.?", "PIAZZALE"),
                    new Sostituzione("\\bV\\.?\\s?LE\\b\\.?", "VIALE"),
                    new Sostituzione("\\bL\\.?\\s?GO\\b\\.?", "LARGO"),
                    new Sostituzione("\\bC\\.?\\s?SO\\b\\.?", "CORSO"),
                    new Sostituzione("\\bS\\.\\s?P\\.", "STRADA PROVINCIALE"),
                    new Sostituzione("\\bS\\.\\s?S\\.", "STRADA STATALE"),
                    new Sostituzione("\\bLOC\\.", "LOCALITA"));

    /** "SNC", "S.N.C.": senza numero civico, cioè niente da cercare. */
    private static final Pattern SENZA_CIVICO = Pattern.compile("\\bS\\.?\\s?N\\.?\\s?C\\b\\.?");
    /** "KM 11.00", "KM. 21,500": la progressiva chilometrica, che OSM non ha. */
    private static final Pattern CHILOMETRO = Pattern.compile("\\bKM\\.?\\s*[\\d.,]+");
    /** "9/11", "40/5": di un civico doppio si cerca il primo numero. */
    private static final Pattern CIVICO_DOPPIO = Pattern.compile("\\b(\\d+)\\s*/\\s*\\w+");
    /** Il civico in coda, con l'eventuale lettera o la virgola prima: "UMBERTO I, 3". */
    private static final Pattern CIVICO_FINALE = Pattern.compile("[,\\s]+(N\\.?\\s*)?\\d+[A-Z]?$");

    private final SocietaRepository repository;
    private final Geocoding geocoding;
    private final Duration pausa;

    public GeocodificaAutomatica(
            SocietaRepository repository,
            Geocoding geocoding,
            @Value("${trovacampo.geocodifica.intervallo:PT1.1S}") Duration pausa) {
        this.repository = repository;
        this.geocoding = geocoding;
        this.pausa = pausa;
    }

    /** fixedDelay e non fixedRate: l'attesa conta da quando Nominatim ha risposto. */
    @Scheduled(fixedDelayString = "${trovacampo.geocodifica.intervallo:PT1.1S}")
    public void geocodificaLaProssima() {
        try {
            repository.primaDaGeocodificare(VERSIONE).ifPresent(this::geocodifica);
        } catch (RuntimeException eccezione) {
            // Mongo giù per un attimo non deve fermare lo scheduler per sempre.
            log.warn("Geocodifica automatica non riuscita: {}", eccezione.toString());
        }
    }

    void geocodifica(Societa societa) {
        Optional<Geocoding.Coordinate> trovate = Optional.empty();
        List<String> tentativi = tentativi(societa);
        int tentativo = 0;

        for (; tentativo < tentativi.size(); tentativo++) {
            if (tentativo > 0) {
                // Anche il secondo tentativo è una richiesta a Nominatim, e
                // conta per il limite di una al secondo.
                aspetta();
            }
            trovate = geocoding.geocodifica(tentativi.get(tentativo));
            if (trovate.isPresent()) {
                break;
            }
        }

        // Trovato solo al secondo tentativo vuol dire senza il civico: il
        // segnaposto sta sulla via, non per forza davanti al campo.
        Boolean approssimata = tentativo > 0 ? Boolean.TRUE : null;
        trovate.ifPresentOrElse(
                coordinate ->
                        societa.setLat(coordinate.lat())
                                .setLng(coordinate.lng())
                                .setPosizioneApprossimata(approssimata)
                                .setGeocodificaFallitaVersione(null),
                () -> {
                    log.info("Indirizzo non riconosciuto: {}", tentativi);
                    societa.setGeocodificaFallitaVersione(VERSIONE);
                });
        repository.save(societa);
    }

    /**
     * Le ricerche da provare, in ordine: l'indirizzo ripulito, poi la sola
     * via senza civico. Senza doppioni, quando il civico non c'era.
     */
    static List<String> tentativi(Societa societa) {
        String via = pulisci(societa.getIndirizzoImpianto());
        String localita =
                Stream.of(societa.getLocalitaImpianto(), societa.getProvinciaImpianto())
                        .filter(parte -> parte != null && !parte.isBlank())
                        .collect(Collectors.joining(" "));

        Set<String> ricerche = new LinkedHashSet<>();
        ricerche.add(unisci(via, localita));
        ricerche.add(unisci(CIVICO_FINALE.matcher(via).replaceAll("").strip(), localita));
        return List.copyOf(ricerche);
    }

    /** "P.ZA MARTIRI DELLA LIBERTA' 27" diventa "PIAZZA MARTIRI DELLA LIBERTA' 27". */
    static String pulisci(String indirizzo) {
        if (indirizzo == null) {
            return "";
        }
        String pulito = indirizzo.toUpperCase(Locale.ITALIAN);
        for (Sostituzione sostituzione : ABBREVIAZIONI) {
            pulito = sostituzione.applica(pulito);
        }
        pulito = SENZA_CIVICO.matcher(pulito).replaceAll(" ");
        pulito = CHILOMETRO.matcher(pulito).replaceAll(" ");
        pulito = CIVICO_DOPPIO.matcher(pulito).replaceAll("$1");
        return pulito.replaceAll("\\s+", " ").replaceAll("[\\s,]+$", "").strip();
    }

    /**
     * "VIA DELLA CERTOSA 12, ROMA RM, Italia": senza comune e paese Nominatim
     * sceglie la prima via con quel nome, spesso in un'altra regione.
     */
    private static String unisci(String via, String localita) {
        return Stream.of(via, localita, "Italia")
                .filter(parte -> parte != null && !parte.isBlank())
                .collect(Collectors.joining(", "));
    }

    private void aspetta() {
        try {
            Thread.sleep(pausa);
        } catch (InterruptedException eccezione) {
            Thread.currentThread().interrupt();
        }
    }

    private record Sostituzione(Pattern schema, String con) {
        Sostituzione(String schema, String con) {
            this(Pattern.compile(schema), con);
        }

        String applica(String testo) {
            return schema.matcher(testo).replaceAll(con);
        }
    }
}
