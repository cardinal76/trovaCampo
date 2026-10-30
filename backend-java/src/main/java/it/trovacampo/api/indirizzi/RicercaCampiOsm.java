package it.trovacampo.api.indirizzi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import it.trovacampo.api.service.RitmoNominatim;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Fonte 3, l'ultima risorsa: un campo sportivo su OpenStreetMap, cercato con
 * Nominatim per nome della società e comune.
 *
 * <p>Due ricerche al massimo per società, ognuna dopo il turno del
 * {@link RitmoNominatim} (una richiesta al secondo per tutto il backend,
 * User-Agent di TrovaCampo, come chiede la usage policy):
 *
 * <ol>
 *   <li>"&lt;nome senza forma giuridica&gt; &lt;comune&gt;": trova i campi
 *       che su OSM hanno il nome della società ("SS Romulea");
 *   <li>"campo sportivo &lt;comune&gt;": il campo del paese.
 * </ol>
 *
 * <p>Si accettano solo luoghi {@code leisure=pitch}, {@code sports_centre} o
 * {@code stadium} nel comune della sede, dentro il riquadro del Lazio. L'esito
 * ha sempre confidenza BASSA: è un'ipotesi, da controllare. I dati sono
 * © OpenStreetMap contributors, licenza ODbL: l'attribuzione viaggia con
 * l'indirizzo.
 */
@Component
public class RicercaCampiOsm {

    private static final Logger log = LoggerFactory.getLogger(RicercaCampiOsm.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Il riquadro del Lazio (ovest, nord, est, sud), con un po' di margine. */
    static final String RIQUADRO_LAZIO = "11.40,42.90,14.10,40.70";

    private static final Set<String> TIPI_CAMPO = Set.of("pitch", "sports_centre", "stadium");

    static final double PUNTEGGIO_PER_NOME = 0.4;
    static final double PUNTEGGIO_CAMPO_DEL_PAESE = 0.2;

    private final RestClient client;
    private final RitmoNominatim ritmo;

    @Autowired
    public RicercaCampiOsm(
            RestClient.Builder builder,
            @Value("${trovacampo.nominatim.url}") String url,
            @Value("${trovacampo.nominatim.user-agent}") String userAgent,
            RitmoNominatim ritmo) {
        this(builder.baseUrl(url)
                        .requestFactory(richieste())
                        .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                        .build(),
                ritmo);
    }

    RicercaCampiOsm(RestClient client, RitmoNominatim ritmo) {
        this.client = client;
        this.ritmo = ritmo;
    }

    private static SimpleClientHttpRequestFactory richieste() {
        SimpleClientHttpRequestFactory richieste = new SimpleClientHttpRequestFactory();
        richieste.setConnectTimeout(TIMEOUT);
        richieste.setReadTimeout(TIMEOUT);
        return richieste;
    }

    public Optional<IndirizzoMigliore> cerca(SocietaDaLocalizzare societa) {
        if (Comuni.chiave(societa.comune()).isEmpty()) {
            return Optional.empty();
        }
        String nome = String.join(" ", NomiSocieta.parole(societa.denominazione()));
        if (!nome.isEmpty()) {
            Optional<IndirizzoMigliore> perNome =
                    primoCampo(nome + " " + societa.comune(), societa, PUNTEGGIO_PER_NOME);
            if (perNome.isPresent()) {
                return perNome;
            }
        }
        return primoCampo("campo sportivo " + societa.comune(), societa, PUNTEGGIO_CAMPO_DEL_PAESE);
    }

    private Optional<IndirizzoMigliore> primoCampo(
            String ricerca, SocietaDaLocalizzare societa, double punteggio) {
        return luoghi(ricerca).stream()
                .filter(luogo -> "leisure".equals(luogo.category()) && TIPI_CAMPO.contains(luogo.type()))
                .filter(luogo -> Comuni.stesso(luogo.comune(), societa.comune()))
                .findFirst()
                .map(luogo -> luogo.indirizzo(punteggio));
    }

    private List<Luogo> luoghi(String ricerca) {
        try {
            ritmo.attendi();
            Luogo[] luoghi =
                    client.get()
                            .uri(
                                    uri ->
                                            uri.path("/search")
                                                    .queryParam("format", "jsonv2")
                                                    .queryParam("addressdetails", 1)
                                                    .queryParam("countrycodes", "it")
                                                    .queryParam("viewbox", RIQUADRO_LAZIO)
                                                    .queryParam("bounded", 1)
                                                    .queryParam("limit", 5)
                                                    .queryParam("q", ricerca)
                                                    .build())
                            .retrieve()
                            .body(Luogo[].class);
            return luoghi == null ? List.of() : List.of(luoghi);
        } catch (RuntimeException eccezione) {
            log.warn("Ricerca su OSM non riuscita per '{}': {}", ricerca, eccezione.toString());
            return List.of();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Luogo(
            String lat, String lon, String category, String type, String name, Map<String, String> address) {

        /** Nominatim mette il comune in city, town, village o municipality, secondo la grandezza. */
        String comune() {
            if (address == null) {
                return "";
            }
            return Stream.of("city", "town", "village", "municipality")
                    .map(address::get)
                    .filter(valore -> valore != null && !valore.isBlank())
                    .findFirst()
                    .orElse("");
        }

        IndirizzoMigliore indirizzo(double punteggio) {
            String via =
                    address == null
                            ? ""
                            : Stream.of(address.get("road"), address.get("house_number"))
                                    .filter(parte -> parte != null && !parte.isBlank())
                                    .collect(Collectors.joining(" "));
            return new IndirizzoMigliore(
                    via.isEmpty() ? name : via,
                    comune(),
                    Double.parseDouble(lat),
                    Double.parseDouble(lon),
                    Fonte.GEOCODING_OSM,
                    Confidenza.BASSA,
                    punteggio,
                    name,
                    name,
                    Fonte.GEOCODING_OSM.attribuzione());
        }
    }
}
