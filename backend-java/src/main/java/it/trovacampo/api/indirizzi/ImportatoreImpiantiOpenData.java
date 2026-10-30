package it.trovacampo.api.indirizzi;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Importa nella collection {@code impianto_open_data} il «Censimento impianti
 * sportivi di Roma e Provincia» della Città metropolitana di Roma Capitale
 * (licenza CC0 1.0, opendata.cittametropolitanaroma.it), tenendo solo gli
 * impianti con almeno un campo da calcio.
 *
 * <p>Il file ha una riga per impianto e disciplina: si raggruppano per nome,
 * comune e indirizzo. Non ha coordinate: le dà la geocodifica dell'indirizzo
 * quando un impianto viene abbinato a una società, una volta sola.
 *
 * <p>Riproducibile: lo stesso file reimportato sovrascrive le stesse righe.
 * Una richiesta sola, con User-Agent che dice chi la fa.
 */
@Service
public class ImportatoreImpiantiOpenData {

    private static final Logger log = LoggerFactory.getLogger(ImportatoreImpiantiOpenData.class);

    static final String FONTE = "cmrc-censimento";
    static final String LICENZA = "CC0 1.0";

    private final ImpiantiOpenDataRepository repository;
    private final RestClient client;
    private final String url;
    private final Clock orologio;

    @Autowired
    public ImportatoreImpiantiOpenData(
            ImpiantiOpenDataRepository repository,
            RestClient.Builder builder,
            @Value("${trovacampo.indirizzi.open-data.cmrc-url}") String url,
            @Value("${trovacampo.nominatim.user-agent}") String userAgent) {
        this(repository, builder.requestFactory(richieste())
                        .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                        .build(),
                url, Clock.systemUTC());
    }

    ImportatoreImpiantiOpenData(
            ImpiantiOpenDataRepository repository, RestClient client, String url, Clock orologio) {
        this.repository = repository;
        this.client = client;
        this.url = url;
        this.orologio = orologio;
    }

    private static SimpleClientHttpRequestFactory richieste() {
        SimpleClientHttpRequestFactory richieste = new SimpleClientHttpRequestFactory();
        richieste.setConnectTimeout(Duration.ofSeconds(10));
        richieste.setReadTimeout(Duration.ofSeconds(60));
        return richieste;
    }

    /**
     * @param righe le righe del file, intestazione esclusa
     * @param impianti gli impianti distinti con almeno un campo da calcio
     * @param conCalcioA11 quanti di questi hanno un campo a 11
     */
    public record Esito(String fonte, String licenza, String url, int righe, int impianti, int conCalcioA11) {}

    public Esito importa() {
        byte[] contenuto = client.get().uri(url).retrieve().body(byte[].class);
        if (contenuto == null || contenuto.length == 0) {
            throw new IllegalStateException("Il censimento è vuoto: " + url);
        }
        List<List<String>> righe = LettoreCsv.leggi(new String(contenuto, StandardCharsets.UTF_8), ',');
        List<ImpiantoOpenData> impianti = impianti(righe, orologio.instant());
        repository.saveAll(impianti);

        Esito esito =
                new Esito(
                        FONTE, LICENZA, url, Math.max(0, righe.size() - 1), impianti.size(),
                        (int) impianti.stream().filter(ImpiantoOpenData::haCalcioA11).count());
        log.info("Censimento impianti open data importato: {}", esito);
        return esito;
    }

    /** Gli impianti con calcio, uno per nome, comune e indirizzo, con tutte le loro discipline. */
    static List<ImpiantoOpenData> impianti(List<List<String>> righe, Instant adesso) {
        if (righe.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> colonne = colonne(righe.get(0));
        for (String necessaria : List.of("nomeimpianto", "comune", "indirizzo", "sport")) {
            if (!colonne.containsKey(necessaria)) {
                throw new IllegalStateException(
                        "Il censimento non ha la colonna " + necessaria + ": è cambiato il formato?");
            }
        }

        Map<String, Gruppo> gruppi = new LinkedHashMap<>();
        for (List<String> riga : righe.subList(1, righe.size())) {
            String nome = valore(riga, colonne, "nomeimpianto");
            String comune = valore(riga, colonne, "comune");
            String indirizzo = valore(riga, colonne, "indirizzo");
            String sport = valore(riga, colonne, "sport");
            String struttura = valore(riga, colonne, "struttura");
            if (nome.isEmpty() || comune.isEmpty()) {
                continue;
            }
            String chiave =
                    FONTE + "|" + String.join(" ", NomiSocieta.parole(nome)) + "|" + Comuni.chiave(comune)
                            + "|" + indirizzo.toLowerCase(Locale.ITALIAN).replaceAll("[^a-z0-9]+", " ").strip();
            Gruppo gruppo =
                    gruppi.computeIfAbsent(
                            chiave,
                            k -> new Gruppo(k, nome, comune, valore(riga, colonne, "municipio"), indirizzo));
            if (sport.toLowerCase(Locale.ITALIAN).startsWith("calcio")) {
                gruppo.sport.add(sport);
            } else if (struttura.toLowerCase(Locale.ITALIAN).contains("calcio")) {
                gruppo.calcioNellaStruttura = true;
            }
        }

        List<ImpiantoOpenData> impianti = new ArrayList<>();
        for (Gruppo gruppo : gruppi.values()) {
            if (gruppo.sport.isEmpty() && gruppo.calcioNellaStruttura) {
                // Qualche riga del file ha le virgolette sbagliate e la
                // disciplina finisce dentro la struttura: il campo c'è lo stesso.
                gruppo.sport.add("Calcio");
            }
            if (!gruppo.sport.isEmpty()) {
                impianti.add(
                        new ImpiantoOpenData(
                                gruppo.id, FONTE, LICENZA, gruppo.nome, gruppo.comune,
                                gruppo.municipio.isEmpty() ? null : gruppo.municipio,
                                gruppo.indirizzo, List.copyOf(gruppo.sport), null, null, adesso));
            }
        }
        return impianti;
    }

    private static Map<String, Integer> colonne(List<String> intestazione) {
        Map<String, Integer> colonne = new LinkedHashMap<>();
        for (int i = 0; i < intestazione.size(); i++) {
            colonne.put(intestazione.get(i).strip().toLowerCase(Locale.ROOT), i);
        }
        return colonne;
    }

    private static String valore(List<String> riga, Map<String, Integer> colonne, String nome) {
        Integer indice = colonne.get(nome);
        if (indice == null || indice >= riga.size()) {
            return "";
        }
        return riga.get(indice).strip().replaceAll("\\s+", " ");
    }

    private static final class Gruppo {
        final String id;
        final String nome;
        final String comune;
        final String municipio;
        final String indirizzo;
        final Set<String> sport = new LinkedHashSet<>();
        boolean calcioNellaStruttura;

        Gruppo(String id, String nome, String comune, String municipio, String indirizzo) {
            this.id = id;
            this.nome = nome;
            this.comune = comune;
            this.municipio = municipio;
            this.indirizzo = indirizzo;
        }
    }
}
