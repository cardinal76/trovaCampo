package it.trovacampo.api.prospect;

import it.trovacampo.api.anagrafica.AnagraficaPresenze;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dà l'indirizzo del campo di gioco ai prospect di calcio del Lazio,
 * prendendolo dai campi che TrovaCampo ha già (comunicati LND, file,
 * correzioni a mano) e, in seconda battuta, dall'anagrafica di presenze.
 *
 * <p>Il registro non ha né la matricola FIGC né un indirizzo, e i comunicati
 * non hanno il codice fiscale: il collegamento è per nome, ridotto alle
 * parole che distinguono la società ({@link NomiSocieta}), con il comune a
 * decidere fra più candidati. Le regole, dalla più sicura:
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
 * Nel dubbio non si collega: un prospect senza indirizzo si vede che manca,
 * uno con l'indirizzo di un'altra società no.
 */
@Service
public class CollegamentoCampi {

    private static final Logger log = LoggerFactory.getLogger(CollegamentoCampi.class);

    static final String LAZIO = "Lazio";

    private final ProspectRepository prospect;
    private final SocietaRepository societa;
    private final AnagraficaPresenze anagrafica;

    public CollegamentoCampi(
            ProspectRepository prospect, SocietaRepository societa, AnagraficaPresenze anagrafica) {
        this.prospect = prospect;
        this.societa = societa;
        this.anagrafica = anagrafica;
    }

    /** Un campo dove una società gioca: da una scheda di TrovaCampo o da presenze. */
    record Candidato(
            String chiave,
            Set<String> parole,
            String comune,
            FonteIndirizzo fonte,
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

    record Abbinamento(Candidato campo, String regola) {}

    public record Esito(
            int prospectCalcioLazio,
            int collegati,
            int conCoordinate,
            Map<FonteIndirizzo, Integer> perFonte,
            Map<String, Integer> perRegola,
            boolean presenzeRaggiungibile) {

        /** La percentuale di società di calcio del Lazio con l'indirizzo del campo. */
        public double copertura() {
            return prospectCalcioLazio == 0 ? 0 : Math.round(1000.0 * collegati / prospectCalcioLazio) / 10.0;
        }
    }

    public Esito collega() {
        List<Prospect> daCollegare = prospect.findByRegioneAndCalcioTrue(LAZIO);
        boolean[] presenzeRaggiungibile = {true};
        List<Candidato> candidati = candidati(presenzeRaggiungibile);
        Map<String, List<Candidato>> perChiave = new LinkedHashMap<>();
        for (Candidato candidato : candidati) {
            perChiave.computeIfAbsent(candidato.chiave(), k -> new ArrayList<>()).add(candidato);
        }

        List<Prospect> cambiati = new ArrayList<>();
        Map<FonteIndirizzo, Integer> perFonte = new EnumMap<>(FonteIndirizzo.class);
        Map<String, Integer> perRegola = new LinkedHashMap<>();
        int collegati = 0;
        int conCoordinate = 0;
        for (Prospect p : daCollegare) {
            Optional<Abbinamento> abbinamento =
                    abbina(p.getDenominazione(), p.getComune(), candidati, perChiave);
            // Il campo di gioco è la fonte preferita: prende il posto di qualunque altra.
            if (abbinamento.isPresent()) {
                applica(p, abbinamento.get());
                cambiati.add(p);
                perRegola.merge(abbinamento.get().regola(), 1, Integer::sum);
            }
            if (p.getFonteIndirizzo() != null) {
                collegati++;
                perFonte.merge(p.getFonteIndirizzo(), 1, Integer::sum);
            }
            if (p.getLat() != null && p.getLng() != null) {
                conCoordinate++;
            }
        }
        if (!cambiati.isEmpty()) {
            prospect.saveAll(cambiati);
        }
        Esito esito =
                new Esito(
                        daCollegare.size(), collegati, conCoordinate, perFonte, perRegola,
                        presenzeRaggiungibile[0]);
        log.info(
                "Collegamento dei prospect del Lazio: {} su {} con il campo ({}%), {} con le coordinate",
                collegati, daCollegare.size(), esito.copertura(), conCoordinate);
        return esito;
    }

    private static void applica(Prospect p, Abbinamento abbinamento) {
        Candidato campo = abbinamento.campo();
        p.setNomeImpianto(campo.nomeImpianto())
                .setIndirizzo(campo.indirizzo())
                .setComuneCampo(campo.comuneCampo())
                .setLat(campo.lat())
                .setLng(campo.lng())
                .setFonteIndirizzo(campo.fonte())
                .setCollegamento(abbinamento.regola())
                .setSocietaTrovaCampoId(campo.societaId())
                .setAnagraficaImpiantoId(campo.impiantoId())
                .setAggiornato(Instant.now());
    }

    private List<Candidato> candidati(boolean[] presenzeRaggiungibile) {
        List<Candidato> candidati = new ArrayList<>();
        for (Societa s : societa.tuttiICampi()) {
            if (s.getLat() == null || s.getLng() == null) {
                continue;
            }
            candidati.add(
                    candidato(
                            s.getNomeSocieta(),
                            s.getLocalitaImpianto(),
                            FonteIndirizzo.CAMPO_TROVACAMPO,
                            s.getNomeImpianto(),
                            s.getIndirizzoImpianto(),
                            s.getLat(),
                            s.getLng(),
                            s.getId(),
                            s.getAnagraficaImpiantoId()));
        }
        try {
            for (AnagraficaPresenze.Impianto impianto : anagrafica.impianti()) {
                if (impianto.societa() == null
                        || impianto.indirizzo() == null
                        || impianto.indirizzo().isBlank()) {
                    continue;
                }
                for (AnagraficaPresenze.Riferimento r : impianto.societa()) {
                    candidati.add(
                            candidato(
                                    r.denominazione(),
                                    impianto.comune(),
                                    FonteIndirizzo.CAMPO_PRESENZE,
                                    impianto.nome(),
                                    impianto.indirizzo(),
                                    impianto.lat(),
                                    impianto.lng(),
                                    null,
                                    impianto.id()));
                }
            }
        } catch (RuntimeException e) {
            // Senza presenze bastano i campi di TrovaCampo, che sono la fonte preferita.
            presenzeRaggiungibile[0] = false;
            log.warn("Anagrafica di presenze non raggiungibile: collego solo i campi di TrovaCampo", e);
        }
        return candidati;
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
                String.join(" ", parole),
                Set.copyOf(parole),
                NomiSocieta.chiave(comune),
                fonte,
                nomeImpianto,
                indirizzo,
                comune,
                lat,
                lng,
                societaId,
                impiantoId);
    }

    /** Il campo di una società del registro, se le regole ne trovano uno solo. */
    static Optional<Abbinamento> abbina(
            String denominazione,
            String comune,
            List<Candidato> candidati,
            Map<String, List<Candidato>> perChiave) {
        List<String> lista = NomiSocieta.parole(denominazione);
        if (lista.isEmpty()) {
            return Optional.empty();
        }
        String chiave = String.join(" ", lista);
        Set<String> parole = Set.copyOf(lista);
        String comuneChiave = NomiSocieta.chiave(comune);

        List<Candidato> stessoNome = perChiave.getOrDefault(chiave, List.of());
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
        for (Candidato c : candidati) {
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
}
