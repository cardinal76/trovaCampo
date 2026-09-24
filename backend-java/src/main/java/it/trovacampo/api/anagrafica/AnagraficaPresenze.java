package it.trovacampo.api.anagrafica;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * L'anagrafica di presenze: società, squadre e campi letti dai Comunicati
 * Ufficiali del Comitato Lazio, dalla sua API pubblica
 * ({@code /api/pubblico/anagrafica}).
 *
 * <p>In produzione i due backend stanno sulla stessa rete Docker e si parlano
 * da dentro ({@code http://presenze-backend:8080}), senza passare da Caddy.
 */
@Component
public class AnagraficaPresenze {

    private static final Duration TIMEOUT_CONNESSIONE = Duration.ofSeconds(5);
    /** L'elenco di tutti i campi del Lazio sono qualche centinaio di KB. */
    private static final Duration TIMEOUT_LETTURA = Duration.ofSeconds(30);

    private final RestClient client;

    @Autowired
    public AnagraficaPresenze(
            RestClient.Builder builder, @Value("${trovacampo.anagrafica.url}") String url) {
        this(builder.baseUrl(url).requestFactory(richieste()).build());
    }

    /** Per i test, con un client già pronto. */
    AnagraficaPresenze(RestClient client) {
        this.client = client;
    }

    private static SimpleClientHttpRequestFactory richieste() {
        SimpleClientHttpRequestFactory richieste = new SimpleClientHttpRequestFactory();
        richieste.setConnectTimeout(TIMEOUT_CONNESSIONE);
        richieste.setReadTimeout(TIMEOUT_LETTURA);
        return richieste;
    }

    /** Tutti i campi, ognuno con le società che ci giocano in casa. */
    public List<Impianto> impianti() {
        Impianto[] impianti =
                client.get().uri("/api/pubblico/anagrafica/impianti").retrieve().body(Impianto[].class);
        return impianti == null ? List.of() : List.of(impianti);
    }

    /** Le società che si chiamano, o si sono chiamate, più o meno così (al massimo trenta). */
    public List<Riferimento> cerca(String nome) {
        Riferimento[] trovate =
                client.get()
                        .uri(
                                uri ->
                                        uri.path("/api/pubblico/anagrafica/societa")
                                                .queryParam("q", nome)
                                                .build())
                        .retrieve()
                        .body(Riferimento[].class);
        return trovate == null ? List.of() : List.of(trovate);
    }

    /** Una società con tutte le sue squadre. */
    public Scheda scheda(long id) {
        return client.get()
                .uri("/api/pubblico/anagrafica/societa/{id}", id)
                .retrieve()
                .body(Scheda.class);
    }

    /**
     * Le partite fra {@code dal} e {@code al} (estremi compresi, al massimo
     * 31 giorni) su tutti i campi, ognuna con l'id del campo dove si gioca:
     * una chiamata sola per tutta la mappa.
     */
    public List<Partita> partite(LocalDate dal, LocalDate al) {
        Partita[] partite =
                client.get()
                        .uri(
                                uri ->
                                        uri.path("/api/pubblico/anagrafica/partite")
                                                .queryParam("dal", dal)
                                                .queryParam("al", al)
                                                .build())
                        .retrieve()
                        .body(Partita[].class);
        return partite == null ? List.of() : List.of(partite);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Impianto(
            Long id,
            String nome,
            String indirizzo,
            String comune,
            Double lat,
            Double lng,
            List<Riferimento> societa) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Riferimento(Long id, String denominazione) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scheda(Long id, String denominazione, List<Campo> campi, List<Squadra> squadre) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Campo(Long id, String nome, String indirizzo, String comune) {}

    /**
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali
     * @param girone vuoto finché i gironi non escono
     * @param squadra vuota per la prima squadra, "B" per la seconda
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Squadra(
            String campionato,
            String ente,
            String stagione,
            String girone,
            String squadra,
            boolean fuoriClassifica,
            Long campoId) {}

    /**
     * Una partita in calendario, sul campo di casa di chi ospita.
     *
     * @param impiantoId il campo in presenze: lo stesso id di {@link Impianto}
     * @param stato DA_GIOCARE, GIOCATA, RINVIATA o SOSPESA
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali
     * @param casaSocietaId la società di casa in presenze, come {@link Riferimento#id()}
     * @param casaSquadra vuota per la prima squadra, "B" per la seconda; null da un
     *     presenze che non la manda ancora (vedi {@code ChiaveSquadra})
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Partita(
            Long id,
            OffsetDateTime dataOra,
            String stato,
            Long impiantoId,
            String casa,
            String ospite,
            String campionato,
            String ente,
            String girone,
            int giornata,
            Long casaSocietaId,
            Long ospiteSocietaId,
            String casaSquadra,
            String ospiteSquadra) {

        /** Senza le squadre: quello che serve a scheda e mappa. */
        public Partita(
                Long id,
                OffsetDateTime dataOra,
                String stato,
                Long impiantoId,
                String casa,
                String ospite,
                String campionato,
                String ente,
                String girone,
                int giornata) {
            this(id, dataOra, stato, impiantoId, casa, ospite, campionato, ente, girone, giornata,
                    null, null, null, null);
        }
    }
}
