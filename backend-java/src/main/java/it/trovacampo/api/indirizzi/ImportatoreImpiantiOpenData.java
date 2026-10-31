package it.trovacampo.api.indirizzi;

import it.trovacampo.api.service.LimitatoreRichieste;
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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Importa nella collection {@code impianto_open_data} un censimento degli
 * impianti sportivi in open data ({@link FonteOpenData}), tenendo solo gli
 * impianti con almeno un campo da calcio:
 *
 * <ul>
 *   <li>{@link FonteOpenData#CMRC}: il «Censimento impianti sportivi di Roma e
 *       Provincia» della Città metropolitana di Roma Capitale (CC0 1.0,
 *       opendata.cittametropolitanaroma.it). Una riga per impianto e
 *       disciplina, che si raggruppano per nome, comune e indirizzo; niente
 *       coordinate. Una richiesta sola;
 *   <li>{@link FonteOpenData#VENETO}: i dataset comunali di dati.veneto.it
 *       (CC BY 4.0), uno per comune ({@link OpenDataVeneto}), quasi tutti con
 *       le coordinate. Una richiesta per dataset, dieci secondi fra l'una e
 *       l'altra come chiede il robots.txt del portale.
 * </ul>
 *
 * <p>Gli impianti senza coordinate le ricevono dalla geocodifica
 * dell'indirizzo quando vengono abbinati a una società, una volta sola.
 *
 * <p>Riproducibile: lo stesso file reimportato sovrascrive le stesse righe.
 * User-Agent che dice chi fa le richieste; una risposta 401, 403, 407, 429,
 * 503 o una pagina HTML al posto del CSV (un captcha, un WAF) ferma tutto con
 * {@link OpenDataBloccatoException}, senza riprovare. Lo lancia solo
 * l'amministrazione, a mano: nessun giro programmato.
 */
@Service
@EnableConfigurationProperties(OpenDataVeneto.class)
public class ImportatoreImpiantiOpenData {

    private static final Logger log = LoggerFactory.getLogger(ImportatoreImpiantiOpenData.class);

    static final String FONTE = FonteOpenData.CMRC.sigla();
    static final String LICENZA = FonteOpenData.CMRC.licenza();
    private static final Set<Integer> STATI_DI_BLOCCO = Set.of(401, 403, 407, 429, 503);

    private final ImpiantiOpenDataRepository repository;
    private final RestClient client;
    private final String url;
    private final OpenDataVeneto veneto;
    private final LimitatoreRichieste.Attesa attesa;
    private final Clock orologio;

    @Autowired
    public ImportatoreImpiantiOpenData(
            ImpiantiOpenDataRepository repository,
            RestClient.Builder builder,
            @Value("${trovacampo.indirizzi.open-data.cmrc-url}") String url,
            OpenDataVeneto veneto,
            @Value("${trovacampo.nominatim.user-agent}") String userAgent) {
        this(repository, builder.requestFactory(richieste())
                        .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                        .build(),
                url, veneto, Thread::sleep, Clock.systemUTC());
    }

    ImportatoreImpiantiOpenData(
            ImpiantiOpenDataRepository repository,
            RestClient client,
            String url,
            OpenDataVeneto veneto,
            LimitatoreRichieste.Attesa attesa,
            Clock orologio) {
        this.repository = repository;
        this.client = client;
        this.url = url;
        this.veneto = veneto;
        this.attesa = attesa;
        this.orologio = orologio;
    }

    private static SimpleClientHttpRequestFactory richieste() {
        SimpleClientHttpRequestFactory richieste = new SimpleClientHttpRequestFactory();
        richieste.setConnectTimeout(Duration.ofSeconds(10));
        richieste.setReadTimeout(Duration.ofSeconds(60));
        return richieste;
    }

    /**
     * @param righe le righe dei file, intestazioni escluse
     * @param impianti gli impianti distinti con almeno un campo da calcio
     * @param conCalcioA11 quanti di questi hanno un campo a 11
     * @param conCoordinate quanti hanno già la posizione nel dataset
     * @param nonLetti i dataset che non si sono potuti leggere, con il motivo
     *     (un timeout, un 404): gli altri si importano lo stesso
     */
    public record Esito(
            String fonte,
            String licenza,
            String url,
            int righe,
            int impianti,
            int conCalcioA11,
            int conCoordinate,
            List<String> nonLetti) {}

    /** Il server della fonte ha risposto come chi blocca: ci si ferma e lo si dice. */
    public static class OpenDataBloccatoException extends RuntimeException {
        public OpenDataBloccatoException(String messaggio) {
            super(messaggio);
        }
    }

    /** Il censimento di Roma, come prima che ci fossero altre fonti. */
    public Esito importa() {
        return importa(FonteOpenData.CMRC);
    }

    public Esito importa(FonteOpenData fonte) {
        Esito esito =
                switch (fonte) {
                    case CMRC -> importaCmrc();
                    case VENETO -> importaVeneto();
                };
        log.info("Impianti open data importati: {}", esito);
        return esito;
    }

    private Esito importaCmrc() {
        byte[] contenuto = scarica(url);
        if (contenuto.length == 0) {
            throw new IllegalStateException("Il censimento è vuoto: " + url);
        }
        List<List<String>> righe = LettoreCsv.leggi(LettoreCsv.testo(contenuto), ',');
        List<ImpiantoOpenData> impianti = impianti(righe, orologio.instant());
        repository.saveAll(impianti);
        return esito(FonteOpenData.CMRC, url, Math.max(0, righe.size() - 1), impianti, List.of());
    }

    private Esito importaVeneto() {
        if (veneto.dataset().isEmpty()) {
            throw new IllegalStateException(
                    "Nessun dataset del Veneto configurato (trovacampo.indirizzi.open-data.veneto.dataset)");
        }
        LimitatoreRichieste ritmo = new LimitatoreRichieste(veneto.intervallo(), System::nanoTime, attesa);
        Instant adesso = orologio.instant();
        int righe = 0;
        List<ImpiantoOpenData> impianti = new ArrayList<>();
        List<String> nonLetti = new ArrayList<>();
        for (OpenDataVeneto.Dataset dataset : veneto.dataset()) {
            try {
                ritmo.prima();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Importazione del Veneto interrotta", e);
            }
            try {
                String testo = LettoreCsv.testo(scarica(dataset.url()));
                List<List<String>> lette = LettoreCsv.leggi(testo, LettoreCsv.separatore(testo));
                List<ImpiantoOpenData> delComune = ImpiantiVeneto.impianti(lette, dataset, adesso);
                righe += Math.max(0, lette.size() - 1);
                impianti.addAll(delComune);
                log.info("{}: {} impianti con calcio", dataset.titolo(), delComune.size());
            } catch (RestClientException | IllegalStateException e) {
                // Un file che non risponde o è cambiato non ferma gli altri; un blocco sì (non si prende qui).
                log.warn("{} non letto: {}", dataset.titolo(), e.toString());
                nonLetti.add(dataset.titolo() + ": " + e.getMessage());
            }
        }
        repository.saveAll(impianti);
        return esito(FonteOpenData.VENETO, "https://dati.veneto.it", righe, impianti, nonLetti);
    }

    private static Esito esito(
            FonteOpenData fonte, String url, int righe, List<ImpiantoOpenData> impianti, List<String> nonLetti) {
        return new Esito(
                fonte.sigla(), fonte.licenza(), url, righe, impianti.size(),
                (int) impianti.stream().filter(ImpiantoOpenData::haCalcioA11).count(),
                (int) impianti.stream().filter(impianto -> impianto.lat() != null && impianto.lng() != null).count(),
                List.copyOf(nonLetti));
    }

    private byte[] scarica(String indirizzo) {
        byte[] contenuto =
                client.get()
                        .uri(indirizzo)
                        .exchange(
                                (richiesta, risposta) -> {
                                    HttpStatusCode stato = risposta.getStatusCode();
                                    if (STATI_DI_BLOCCO.contains(stato.value())) {
                                        throw new OpenDataBloccatoException(
                                                richiesta.getURI().getHost() + " ha risposto " + stato.value()
                                                        + ": importazione fermata, senza riprovare.");
                                    }
                                    if (!stato.is2xxSuccessful()) {
                                        throw new IllegalStateException(
                                                "risposta " + stato.value() + " da " + richiesta.getURI());
                                    }
                                    MediaType tipo = risposta.getHeaders().getContentType();
                                    if (tipo != null && tipo.isCompatibleWith(MediaType.TEXT_HTML)) {
                                        // Un captcha o la pagina di un WAF arrivano in HTML con 200.
                                        throw new OpenDataBloccatoException(
                                                richiesta.getURI().getHost() + " ha risposto con una pagina HTML"
                                                        + " invece del CSV: forse una protezione anti-bot,"
                                                        + " importazione fermata.");
                                    }
                                    return risposta.getBody().readAllBytes();
                                });
        return contenuto == null ? new byte[0] : contenuto;
    }

    /**
     * L'id di un impianto: fonte, nome, comune e indirizzo normalizzati, con
     * {@link NomiSocieta} e {@link Comuni} come per l'abbinamento.
     */
    static String id(FonteOpenData fonte, String nome, String comune, String indirizzo) {
        return fonte.sigla() + "|" + String.join(" ", NomiSocieta.parole(nome)) + "|" + Comuni.chiave(comune)
                + "|" + indirizzo.toLowerCase(Locale.ITALIAN).replaceAll("[^a-z0-9]+", " ").strip();
    }

    /** Gli impianti con calcio del censimento di Roma, uno per nome, comune e indirizzo, con tutte le discipline. */
    static List<ImpiantoOpenData> impianti(List<List<String>> righe, Instant adesso) {
        if (righe.isEmpty()) {
            return List.of();
        }
        LettoreCsv.Colonne colonne = new LettoreCsv.Colonne(righe.get(0));
        for (String necessaria : List.of("nomeimpianto", "comune", "indirizzo", "sport")) {
            if (!colonne.ha(necessaria)) {
                throw new IllegalStateException(
                        "Il censimento non ha la colonna " + necessaria + ": è cambiato il formato?");
            }
        }

        Map<String, Gruppo> gruppi = new LinkedHashMap<>();
        for (List<String> riga : righe.subList(1, righe.size())) {
            String nome = colonne.valore(riga, "nomeimpianto");
            String comune = colonne.valore(riga, "comune");
            String indirizzo = colonne.valore(riga, "indirizzo");
            String sport = colonne.valore(riga, "sport");
            String struttura = colonne.valore(riga, "struttura");
            if (nome.isEmpty() || comune.isEmpty()) {
                continue;
            }
            Gruppo gruppo =
                    gruppi.computeIfAbsent(
                            id(FonteOpenData.CMRC, nome, comune, indirizzo),
                            k -> new Gruppo(k, nome, comune, colonne.valore(riga, "municipio"), indirizzo));
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
                                gruppo.indirizzo, List.copyOf(gruppo.sport), null, null, adesso,
                                FonteOpenData.CMRC.attribuzione()));
            }
        }
        return impianti;
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
