package it.trovacampo.api.anagrafica;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
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

    /**
     * Tutte le società di presenze che hanno almeno una squadra, ognuna col
     * suo campo principale: nullo quando il comitato non l'ha ancora detto.
     */
    public List<SocietaConCampo> societa() {
        SocietaConCampo[] tutte =
                client.get()
                        .uri("/api/pubblico/anagrafica/societa")
                        .retrieve()
                        .body(SocietaConCampo[].class);
        return tutte == null ? List.of() : List.of(tutte);
    }

    /** Una società con tutte le sue squadre. */
    public Scheda scheda(long id) {
        return client.get()
                .uri("/api/pubblico/anagrafica/societa/{id}", id)
                .retrieve()
                .body(Scheda.class);
    }

    /**
     * L'ultima partita giocata da <b>ogni squadra</b> della società, dalla
     * più recente.
     *
     * <p><b>Nessun numero da passare</b>: quante righe tornano lo decidono le
     * squadre che la società ha in campo. Prima si chiedevano «le ultime
     * dieci partite» e quel taglio mescolava le categorie -- dieci risultati
     * potevano essere quattro dell'Under 17 e nessuno della prima squadra,
     * perché un girone gioca il sabato e un altro la domenica.
     *
     * <p>Questo metodo e {@link Risultati} sono l'unico posto dove sta scritta
     * la forma della risposta di presenze: un campo che lì cambia nome si
     * corregge qui, e non si va a caccia nel resto del backend o nell'app.
     *
     * <p>Da un presenze che non ha ancora l'endpoint, o che non conosce questa
     * società, solleva {@code HttpClientErrorException.NotFound}: chi chiama
     * lo distingue da un guasto, perché le due cose si raccontano in modo
     * diverso a chi guarda.
     */
    public Risultati risultati(long societaId) {
        return client.get()
                .uri(uri -> uri.path("/api/pubblico/anagrafica/societa/{id}/risultati").build(societaId))
                .retrieve()
                .body(Risultati.class);
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

    /**
     * Solo i punteggi in diretta di queste partite, per id: quello che scheda e
     * mappa chiedono ogni minuto mentre si gioca, invece di tutte le partite.
     * Una partita che non c'è nella risposta non è in diretta.
     */
    public Map<Long, Live> live(Collection<Long> partite) {
        if (partite.isEmpty()) {
            return Map.of();
        }
        String elenco = partite.stream().map(String::valueOf).collect(Collectors.joining(","));
        Map<Long, Live> punteggi =
                client.get()
                        .uri(uri -> uri.path("/api/pubblico/live").queryParam("partite", elenco).build())
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<Long, Live>>() {});
        return punteggi == null ? Map.of() : punteggi;
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

    /**
     * @param logoUrl lo stemma della società sul portale LND; nullo quando
     *     presenze non ne ha uno, o da un presenze che non lo manda ancora
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Riferimento(Long id, String denominazione, String logoUrl) {

        /** Senza stemma: quello che serve alla ricerca per nome. */
        public Riferimento(Long id, String denominazione) {
            this(id, denominazione, null);
        }
    }

    /** @param campo il campo principale, nullo se presenze non lo conosce */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SocietaConCampo(Long id, String denominazione, Campo campo, String logoUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Scheda(Long id, String denominazione, List<Campo> campi, List<Squadra> squadre) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Campo(Long id, String nome, String indirizzo, String comune) {}

    /**
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali
     * @param girone vuoto finché i gironi non escono
     * @param squadra vuota per la prima squadra, "B" per la seconda
     * @param risultatiDaComunicato a quale Comunicato Ufficiale sono aggiornati i
     *     risultati del girone, solo mentre il portale LND non risponde a presenze;
     *     nullo con il portale disponibile, e da un presenze che non lo manda ancora
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Squadra(
            String campionato,
            String ente,
            String stagione,
            String girone,
            String squadra,
            boolean fuoriClassifica,
            Long campoId,
            RisultatiDaComunicato risultatiDaComunicato) {

        /** Senza l'avviso sui risultati: com'era prima che presenze lo mandasse. */
        public Squadra(
                String campionato,
                String ente,
                String stagione,
                String girone,
                String squadra,
                boolean fuoriClassifica,
                Long campoId) {
            this(campionato, ente, stagione, girone, squadra, fuoriClassifica, campoId, null);
        }
    }

    /**
     * Gli ultimi risultati di una società, come li manda presenze.
     *
     * @param societa la società chiesta: il punto di vista da cui è scritto
     *     ogni {@link Risultato#esito()}
     * @param risultati una riga per squadra -- l'ultima partita che ognuna ha
     *     giocato -- dalla più recente, già in quest'ordine;
     *     vuoto a inizio stagione, e non è un errore
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Risultati(Riferimento societa, List<Risultato> risultati) {}

    /**
     * Una partita giocata dalla società, col suo esito.
     *
     * <p>Ci sono solo le partite davvero giocate, con entrambi i gol: una
     * sospesa o una ancora da giocare non arriva. Niente marcatori: il
     * tabellino individuale dei campionati giovanili non esce dall'area
     * autenticata di presenze, perché i calciatori sono minorenni, e una
     * scheda di risultati non ha bisogno di nomi di ragazzi.
     *
     * @param data nulla per una partita di cui presenze non sa ancora l'ora
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali;
     *     può mancare
     * @param girone può mancare finché i gironi non escono
     * @param squadra vuota per la prima squadra, "B" per la seconda
     * @param squadraIscrittaId la squadra della riga. È la chiave della riga,
     *     perché {@code partitaId} non basta: nel derby fra la prima e la
     *     squadra B la stessa partita arriva due volte, una per squadra
     * @param inCasa da che parte giocava la squadra della riga
     * @param esito VITTORIA, PAREGGIO o SCONFITTA, dal punto di vista della
     *     squadra della riga: lo decide presenze, e non si ricalcola
     *     confrontando i gol
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Risultato(
            Long partitaId,
            Long squadraIscrittaId,
            OffsetDateTime data,
            Long campionatoId,
            String campionato,
            String ente,
            String stagione,
            Long gironeId,
            String girone,
            Integer giornata,
            boolean ritorno,
            String squadra,
            String casa,
            String ospite,
            Integer golCasa,
            Integer golOspite,
            boolean inCasa,
            String esito) {}

    /**
     * Da dove vengono i risultati di un girone mentre il portale LND non
     * risponde a presenze: solo dal Comunicato Ufficiale del giovedì o del
     * venerdì, e nel fine settimana classifica e risultati restano fermi.
     *
     * @param numero l'ultimo comunicato che ha portato risultati nel girone;
     *     nullo se nessuno ne ha ancora portati
     * @param data quando è uscito
     * @param ente chi l'ha pubblicato: i numeri ripartono per ogni ente
     * @param portaleNonDisponibileDal da quando il portale non risponde
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RisultatiDaComunicato(
            Integer numero, LocalDate data, String ente, Instant portaleNonDisponibileDal) {}

    /**
     * Una partita in calendario, sul campo di casa di chi ospita.
     *
     * @param impiantoId il campo in presenze: lo stesso id di {@link Impianto}
     * @param stato DA_GIOCARE, GIOCATA, RINVIATA o SOSPESA
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali
     * @param casaSocietaId la società di casa in presenze, come {@link Riferimento#id()}
     * @param casaSquadra vuota per la prima squadra, "B" per la seconda; null da un
     *     presenze che non la manda ancora (vedi {@code ChiaveSquadra})
     * @param live il punteggio in diretta, se la partita si sta giocando e la
     *     società lo sta scrivendo; null altrimenti, o da un presenze che non lo
     *     manda ancora
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Partita(
            Long id,
            OffsetDateTime dataOra,
            String stato,
            Long impiantoId,
            String casa,
            String ospite,
            Long campionatoId,
            String campionato,
            String ente,
            Long gironeId,
            String girone,
            int giornata,
            Long casaSocietaId,
            Long ospiteSocietaId,
            String casaSquadra,
            String ospiteSquadra,
            Live live) {

        /** Senza diretta. */
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
                int giornata,
                Long casaSocietaId,
                Long ospiteSocietaId,
                String casaSquadra,
                String ospiteSquadra) {
            this(id, dataOra, stato, impiantoId, casa, ospite, null, campionato, ente, null, girone,
                    giornata, casaSocietaId, ospiteSocietaId, casaSquadra, ospiteSquadra, null);
        }

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
            this(id, dataOra, stato, impiantoId, casa, ospite, null, campionato, ente, null, girone,
                    giornata, null, null, null, null, null);
        }
    }

    /**
     * Il punteggio di una partita che si sta giocando, scritto dallo staff della
     * società in presenze: è quello che fa comparire l'etichetta LIVE.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Live(int golCasa, int golOspite) {}
}
