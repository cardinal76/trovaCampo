package it.trovacampo.api.indirizzi;

import it.trovacampo.api.anagrafica.AnagraficaPresenze;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Fonte 1: il campo di gioco dai comunicati LND, cioè le schede di TrovaCampo
 * (comunicati, file, correzioni a mano) e, in seconda battuta, l'anagrafica
 * di presenze ({@code /api/pubblico/anagrafica/impianti}).
 *
 * <p>Il registro non ha né la matricola FIGC né un indirizzo, e i comunicati
 * non hanno il codice fiscale: il collegamento è per nome, ridotto alle
 * parole che distinguono la società ({@link NomiSocieta#chiave}), con il
 * comune a decidere fra più candidati. Le regole, dalla più sicura:
 *
 * <ol>
 *   <li><b>nome</b>: stesse parole distintive, almeno due, o una sola che
 *       non sia il comune di un altro campo: «COLONNA» vale per la società con
 *       sede a Colonna, «FROSINONE» non vale per una società di Ripi;
 *   <li><b>parole</b>: le parole di uno stanno tutte nell'altro, almeno due,
 *       e il candidato è uno solo; se sono più d'uno decide il comune;
 *   <li>con il comune uguale basta anche una parola sola, di almeno cinque
 *       lettere e che non sia il comune («VIRTUS PRENESTINO» e «PRENESTINO»).
 * </ol>
 *
 * Nel dubbio non si collega: una società senza indirizzo si vede che manca,
 * una con l'indirizzo di un'altra società no. Confidenza ALTA per
 * {@code nome} e {@code nome+comune}, MEDIA per le regole sulle parole.
 *
 * <p>I candidati si rileggono al massimo ogni dieci minuti, o quando lo
 * chiede {@link #ricarica()}: un collegamento fa centinaia di ricerche.
 */
@Component
public class FonteCampiLnd {

    private static final Logger log = LoggerFactory.getLogger(FonteCampiLnd.class);
    private static final Duration VALIDITA = Duration.ofMinutes(10);

    private final SocietaRepository societa;
    private final AnagraficaPresenze anagrafica;
    private final Clock orologio;
    private Indice indice;
    private Instant lettoIl;

    @Autowired
    public FonteCampiLnd(SocietaRepository societa, AnagraficaPresenze anagrafica) {
        this(societa, anagrafica, Clock.systemUTC());
    }

    FonteCampiLnd(SocietaRepository societa, AnagraficaPresenze anagrafica, Clock orologio) {
        this.societa = societa;
        this.anagrafica = anagrafica;
        this.orologio = orologio;
    }

    /** Un campo dove una società gioca: da una scheda di TrovaCampo o da presenze. */
    record Candidato(
            String chiave,
            Set<String> parole,
            String comune,
            FonteIndirizzo fonte,
            String nomeSocieta,
            String nomeImpianto,
            String indirizzo,
            String comuneCampo,
            Double lat,
            Double lng,
            String societaId,
            Long impiantoId) {

        boolean conCoordinate() {
            return lat != null && lng != null;
        }
    }

    /** I candidati, anche per chiave, e se presenze ha risposto. */
    record Indice(List<Candidato> candidati, Map<String, List<Candidato>> perChiave, boolean presenzeRaggiungibile) {

        static Indice di(List<Candidato> candidati, boolean presenzeRaggiungibile) {
            Map<String, List<Candidato>> perChiave = new LinkedHashMap<>();
            for (Candidato candidato : candidati) {
                perChiave.computeIfAbsent(candidato.chiave(), k -> new ArrayList<>()).add(candidato);
            }
            return new Indice(candidati, perChiave, presenzeRaggiungibile);
        }
    }

    record Abbinamento(Candidato campo, String regola) {}

    public Optional<IndirizzoMigliore> cerca(SocietaDaLocalizzare societa) {
        return abbina(societa.denominazione(), societa.comune(), indice()).map(FonteCampiLnd::indirizzo);
    }

    /** Rilegge i campi adesso: all'inizio di un collegamento, perché valgano gli ultimi importati. */
    public synchronized void ricarica() {
        indice = null;
    }

    /** Se all'ultima lettura presenze ha risposto. */
    public boolean presenzeRaggiungibile() {
        return indice().presenzeRaggiungibile();
    }

    synchronized Indice indice() {
        Instant adesso = orologio.instant();
        if (indice == null || lettoIl.plus(VALIDITA).isBefore(adesso)) {
            indice = leggi();
            lettoIl = adesso;
        }
        return indice;
    }

    private Indice leggi() {
        List<Candidato> candidati = new ArrayList<>();
        for (Societa s : societa.tuttiICampi()) {
            if (s.getLat() == null || s.getLng() == null) {
                continue;
            }
            candidati.add(
                    candidato(
                            s.getNomeSocieta(), s.getLocalitaImpianto(), FonteIndirizzo.CAMPO_TROVACAMPO,
                            s.getNomeImpianto(), s.getIndirizzoImpianto(), s.getLat(), s.getLng(), s.getId(),
                            s.getAnagraficaImpiantoId()));
        }
        boolean presenzeRaggiungibile = true;
        try {
            for (AnagraficaPresenze.Impianto impianto : anagrafica.impianti()) {
                if (impianto.societa() == null || impianto.indirizzo() == null || impianto.indirizzo().isBlank()) {
                    continue;
                }
                for (AnagraficaPresenze.Riferimento r : impianto.societa()) {
                    candidati.add(
                            candidato(
                                    r.denominazione(), impianto.comune(), FonteIndirizzo.CAMPO_PRESENZE,
                                    impianto.nome(), impianto.indirizzo(), impianto.lat(), impianto.lng(), null,
                                    impianto.id()));
                }
            }
        } catch (RuntimeException e) {
            // Senza presenze bastano i campi di TrovaCampo, che sono la fonte preferita.
            presenzeRaggiungibile = false;
            log.warn("Anagrafica di presenze non raggiungibile: solo i campi di TrovaCampo", e);
        }
        return Indice.di(candidati, presenzeRaggiungibile);
    }

    static Candidato candidato(
            String nomeSocieta,
            String comune,
            FonteIndirizzo fonte,
            String nomeImpianto,
            String indirizzo,
            Double lat,
            Double lng,
            String societaId,
            Long impiantoId) {
        List<String> parole = NomiSocieta.parole(nomeSocieta);
        return new Candidato(
                String.join(" ", parole), Set.copyOf(parole), NomiSocieta.chiaveComune(comune), fonte, nomeSocieta,
                nomeImpianto, indirizzo, comune, lat, lng, societaId, impiantoId);
    }

    /** Il campo di una società del registro, se le regole ne trovano uno solo. */
    static Optional<Abbinamento> abbina(String denominazione, String comune, Indice indice) {
        List<String> lista = NomiSocieta.parole(denominazione);
        if (lista.isEmpty()) {
            return Optional.empty();
        }
        String chiave = String.join(" ", lista);
        Set<String> parole = Set.copyOf(lista);
        String comuneChiave = NomiSocieta.chiaveComune(comune);

        List<Candidato> stessoNome = indice.perChiave().getOrDefault(chiave, List.of());
        if (!stessoNome.isEmpty()) {
            if (parole.size() >= 2) {
                return Optional.of(new Abbinamento(migliore(stessoNome, comuneChiave), "nome"));
            }
            // Una parola sola: «MENTANA» per la società di Mentana va bene anche se
            // gioca a Fonte Nuova; «FROSINONE» per una società di Ripi no.
            if (chiave.equals(comuneChiave)) {
                return Optional.of(new Abbinamento(migliore(stessoNome, comuneChiave), "nome+comune"));
            }
            boolean eUnComune = stessoNome.stream().anyMatch(c -> c.comune().equals(chiave));
            return chiave.length() >= 4 && !eUnComune
                    ? Optional.of(new Abbinamento(migliore(stessoNome, comuneChiave), "nome"))
                    : Optional.empty();
        }

        Map<String, List<Candidato>> perParole = new LinkedHashMap<>();
        Map<String, List<Candidato>> unaParolaStessoComune = new LinkedHashMap<>();
        for (Candidato c : indice.candidati()) {
            if (c.parole().isEmpty()) {
                continue;
            }
            boolean contenuto = parole.containsAll(c.parole()) || c.parole().containsAll(parole);
            if (!contenuto) {
                continue;
            }
            Set<String> piccolo = c.parole().size() <= parole.size() ? c.parole() : parole;
            if (piccolo.size() >= 2) {
                perParole.computeIfAbsent(c.chiave(), k -> new ArrayList<>()).add(c);
            } else {
                String sola = piccolo.iterator().next();
                if (sola.length() >= 5
                        && !sola.equals(comuneChiave)
                        && !sola.equals(c.comune())
                        && c.comune().equals(comuneChiave)) {
                    unaParolaStessoComune.computeIfAbsent(c.chiave(), k -> new ArrayList<>()).add(c);
                }
            }
        }
        if (perParole.size() == 1) {
            List<Candidato> trovati = perParole.values().iterator().next();
            return Optional.of(new Abbinamento(migliore(trovati, comuneChiave), "parole"));
        }
        if (perParole.size() > 1) {
            List<List<Candidato>> nelComune =
                    perParole.values().stream()
                            .filter(l -> l.stream().anyMatch(c -> c.comune().equals(comuneChiave)))
                            .toList();
            return nelComune.size() == 1
                    ? Optional.of(new Abbinamento(migliore(nelComune.get(0), comuneChiave), "parole+comune"))
                    : Optional.empty();
        }
        if (unaParolaStessoComune.size() == 1) {
            List<Candidato> trovati = unaParolaStessoComune.values().iterator().next();
            return Optional.of(new Abbinamento(migliore(trovati, comuneChiave), "parola+comune"));
        }
        return Optional.empty();
    }

    /** Fra i campi della stessa società: TrovaCampo prima di presenze, con le coordinate, nel comune. */
    private static Candidato migliore(List<Candidato> campi, String comune) {
        return campi.stream()
                .min(
                        Comparator.comparing((Candidato c) -> c.fonte() != FonteIndirizzo.CAMPO_TROVACAMPO)
                                .thenComparing(c -> !c.conCoordinate())
                                .thenComparing(c -> !c.comune().equals(comune)))
                .orElseThrow();
    }

    private static IndirizzoMigliore indirizzo(Abbinamento abbinamento) {
        Candidato campo = abbinamento.campo();
        boolean perNome = abbinamento.regola().startsWith("nome");
        return new IndirizzoMigliore(
                campo.indirizzo() == null ? null : campo.indirizzo().strip(),
                campo.comuneCampo(),
                campo.lat(),
                campo.lng(),
                campo.fonte(),
                perNome ? Confidenza.ALTA : Confidenza.MEDIA,
                1,
                campo.nomeSocieta(),
                campo.nomeImpianto(),
                campo.fonte().attribuzione(),
                abbinamento.regola(),
                campo.societaId(),
                campo.impiantoId());
    }
}
