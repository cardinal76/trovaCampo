package it.trovacampo.api.prospect;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * La parte pubblica del Registro nazionale delle attività sportive
 * dilettantistiche (https://registro.sportesalute.eu/#/registro/n/), letta
 * dalla stessa API JSON che usa la pagina «Cerca ASD/SSD nel Registro
 * pubblico».
 *
 * <ul>
 *   <li>elenco: {@code /api/istruttoria/lista/?chiamante=registroPubblico_n},
 *       paginato con {@code start} e {@code length}. Senza almeno un filtro
 *       risponde vuoto, quindi si chiede una regione alla volta;
 *   <li>dettaglio: {@code /api/istruttoria/<id>/sidebar/?chiamante=registroPubblico_n},
 *       un elenco di voci etichetta–valore.
 * </ul>
 *
 * <p><b>Buone maniere</b>: ogni richiesta passa dal {@link LimitatoreRichieste}
 * (almeno un secondo fra l'una e l'altra) e porta un User-Agent che dice chi
 * siamo. Una risposta che sa di blocco (403, 429, 503, una pagina HTML al
 * posto del JSON, cioè un captcha o un WAF) solleva
 * {@link RegistroBloccatoException}: chi chiama si ferma e lo racconta,
 * senza riprovare e senza travestirsi da browser.
 *
 * <p><b>GDPR</b>: del dettaglio si leggono solo le voci in
 * {@link #VOCI_DETTAGLIO}. Il nome del legale rappresentante, che il
 * registro mette fra le prime, non esce da questa classe.
 */
@Component
public class RegistroRasd {

    static final String CHIAMANTE = "registroPubblico_n";
    static final String FILTRO_REGIONE = "istruttoria_societa_sedeLegale_regione_id_f";

    static final String VOCE_DISCIPLINE = "Discipline attive";
    static final String VOCE_TESSERATI = "Nr. tesserati attivi";
    static final String VOCE_ATTIVITA = "Nr. attività sportive organizzate nell'ultimo anno";
    static final String VOCE_PARTECIPAZIONI = "Nr. partecipazioni attività sportive nell'ultimo anno";

    /** Le sole voci del dettaglio che si leggono: dati della società, nessuna persona. */
    static final Set<String> VOCI_DETTAGLIO =
            Set.of(VOCE_DISCIPLINE, VOCE_TESSERATI, VOCE_ATTIVITA, VOCE_PARTECIPAZIONI);

    private static final Set<Integer> STATI_DI_BLOCCO = Set.of(401, 403, 407, 429, 503);

    /** Le regioni del filtro del registro, con il suo id (dalla risposta OPTIONS dell'elenco). */
    public static final Map<Integer, String> REGIONI = regioni();

    private final RestClient client;
    private final LimitatoreRichieste limitatore;
    private final ObjectMapper json = new ObjectMapper();

    @Autowired
    public RegistroRasd(
            RestClient.Builder builder,
            @Value("${trovacampo.prospect.rasd.url}") String url,
            @Value("${trovacampo.prospect.rasd.user-agent}") String userAgent,
            @Value("${trovacampo.prospect.rasd.intervallo-ms}") long intervalloMs) {
        this(
                configura(builder.requestFactory(richieste()), url, userAgent).build(),
                new LimitatoreRichieste(Duration.ofMillis(intervalloMs)));
    }

    /** Indirizzo e intestazioni di ogni richiesta; a parte perché i test ci passano un server finto. */
    static RestClient.Builder configura(RestClient.Builder builder, String url, String userAgent) {
        return builder.baseUrl(url)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
    }

    /** Per i test, con un client e un limitatore già pronti. */
    RegistroRasd(RestClient client, LimitatoreRichieste limitatore) {
        this.client = client;
        this.limitatore = limitatore;
    }

    private static SimpleClientHttpRequestFactory richieste() {
        SimpleClientHttpRequestFactory richieste = new SimpleClientHttpRequestFactory();
        richieste.setConnectTimeout(Duration.ofSeconds(10));
        // Una pagina di cento righe arriva in due o tre secondi.
        richieste.setReadTimeout(Duration.ofSeconds(60));
        return richieste;
    }

    private static Map<Integer, String> regioni() {
        Map<Integer, String> regioni = new LinkedHashMap<>();
        // Il Lazio per primo: è il primo lotto.
        regioni.put(12, "Lazio");
        regioni.put(13, "Abruzzo");
        regioni.put(17, "Basilicata");
        regioni.put(18, "Calabria");
        regioni.put(15, "Campania");
        regioni.put(8, "Emilia-Romagna");
        regioni.put(6, "Friuli-Venezia Giulia");
        regioni.put(7, "Liguria");
        regioni.put(3, "Lombardia");
        regioni.put(11, "Marche");
        regioni.put(14, "Molise");
        regioni.put(1, "Piemonte");
        regioni.put(16, "Puglia");
        regioni.put(20, "Sardegna");
        regioni.put(19, "Sicilia");
        regioni.put(9, "Toscana");
        regioni.put(4, "Trentino-Alto Adige/Südtirol");
        regioni.put(10, "Umbria");
        regioni.put(2, "Valle d'Aosta/Vallée d'Aoste");
        regioni.put(5, "Veneto");
        return Collections.unmodifiableMap(regioni);
    }

    /** Una riga dell'elenco: i dati della persona giuridica. */
    public record Voce(
            long id,
            String codiceFiscale,
            String denominazione,
            String regione,
            String comune,
            List<String> organismi,
            String naturaGiuridica,
            LocalDate dataIscrizione) {}

    /** Una pagina dell'elenco, con il totale che il registro dichiara per il filtro. */
    public record Pagina(int totale, List<Voce> voci) {}

    /** Le voci del dettaglio che servono, e nient'altro. */
    public record Dettaglio(
            List<String> discipline,
            Integer tesseratiAttivi,
            Integer attivitaSportiveUltimoAnno,
            Integer partecipazioniUltimoAnno) {}

    /** Il registro ha risposto come chi blocca: ci si ferma e lo si dice. */
    public static class RegistroBloccatoException extends RuntimeException {
        public RegistroBloccatoException(String messaggio) {
            super(messaggio);
        }
    }

    public Pagina pagina(int regioneId, int start, int length) throws InterruptedException {
        JsonNode risposta =
                chiedi(
                        "/api/istruttoria/lista/?chiamante={c}&" + FILTRO_REGIONE + "={r}&start={s}&length={l}",
                        CHIAMANTE,
                        regioneId,
                        start,
                        length);
        JsonNode payload = risposta.path("payload");
        List<Voce> voci = new ArrayList<>();
        for (JsonNode riga : payload.path("data")) {
            String codiceFiscale = testo(riga, "societa__codiceFiscale");
            if (codiceFiscale == null) {
                continue;
            }
            voci.add(
                    new Voce(
                            riga.path("id").asLong(),
                            codiceFiscale.trim().toUpperCase(),
                            testo(riga, "societa__denominazione"),
                            testo(riga, "societa__sedeLegale__regione__denominazione"),
                            testo(riga, "societa__sedeLegale__comune__denominazione"),
                            organismi(riga.path("organismi_affiliazioni_attive")),
                            testo(riga, "societa__natura_giuridica"),
                            data(testo(riga, "approvazione_data"))));
        }
        return new Pagina(payload.path("count").asInt(0), voci);
    }

    public Dettaglio dettaglio(long id) throws InterruptedException {
        JsonNode risposta =
                chiedi("/api/istruttoria/{id}/sidebar/?chiamante={c}", id, CHIAMANTE);
        Map<String, JsonNode> voci = new LinkedHashMap<>();
        for (JsonNode voce : risposta.path("payload").path("corpo").path("dati")) {
            String etichetta = voce.path("label").asText("").trim();
            // Elenco chiuso: tutto il resto, legale rappresentante compreso, non si guarda.
            if (VOCI_DETTAGLIO.contains(etichetta)) {
                voci.put(etichetta, voce.path("value"));
            }
        }
        return new Dettaglio(
                elenco(voci.get(VOCE_DISCIPLINE)),
                numero(voci.get(VOCE_TESSERATI)),
                numero(voci.get(VOCE_ATTIVITA)),
                numero(voci.get(VOCE_PARTECIPAZIONI)));
    }

    private JsonNode chiedi(String uri, Object... variabili) throws InterruptedException {
        limitatore.prima();
        return client.get()
                .uri(uri, variabili)
                .exchange(
                        (richiesta, risposta) -> {
                            HttpStatusCode stato = risposta.getStatusCode();
                            if (STATI_DI_BLOCCO.contains(stato.value())) {
                                throw new RegistroBloccatoException(
                                        "Il registro ha risposto " + stato.value()
                                                + " a " + richiesta.getURI().getPath()
                                                + ": importazione fermata, senza riprovare.");
                            }
                            if (!stato.is2xxSuccessful()) {
                                throw new IllegalStateException(
                                        "Il registro ha risposto " + stato.value()
                                                + " a " + richiesta.getURI().getPath());
                            }
                            MediaType tipo = risposta.getHeaders().getContentType();
                            if (tipo == null || !tipo.isCompatibleWith(MediaType.APPLICATION_JSON)) {
                                // Un captcha o la pagina di un WAF arrivano in HTML con 200.
                                throw new RegistroBloccatoException(
                                        "Il registro ha risposto " + tipo + " invece di JSON a "
                                                + richiesta.getURI().getPath()
                                                + ": forse una protezione anti-bot, importazione fermata.");
                            }
                            return leggi(risposta.getBody().readAllBytes());
                        });
    }

    private JsonNode leggi(byte[] corpo) {
        try {
            return json.readTree(corpo);
        } catch (IOException e) {
            throw new IllegalStateException("Risposta del registro non leggibile", e);
        }
    }

    private static String testo(JsonNode nodo, String campo) {
        JsonNode valore = nodo.get(campo);
        if (valore == null || valore.isNull()) {
            return null;
        }
        String testo = valore.asText().trim();
        return testo.isEmpty() ? null : testo;
    }

    /** «static/os/FIGC.png» → «FIGC»: l'elenco dà gli organismi come icone. */
    static List<String> organismi(JsonNode icone) {
        List<String> sigle = new ArrayList<>();
        for (JsonNode icona : icone) {
            String percorso = icona.asText("");
            String nome = percorso.substring(percorso.lastIndexOf('/') + 1);
            int punto = nome.lastIndexOf('.');
            String sigla = (punto > 0 ? nome.substring(0, punto) : nome).trim();
            if (!sigla.isEmpty() && !sigle.contains(sigla)) {
                sigle.add(sigla);
            }
        }
        return sigle;
    }

    private static LocalDate data(String testo) {
        if (testo == null || testo.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(testo.substring(0, 10));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<String> elenco(JsonNode valore) {
        if (valore == null || valore.isNull()) {
            return List.of();
        }
        return Arrays.stream(valore.asText("").split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    private static Integer numero(JsonNode valore) {
        if (valore == null || valore.isNull()) {
            return null;
        }
        if (valore.isNumber()) {
            return valore.asInt();
        }
        try {
            return Integer.valueOf(valore.asText().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
