package it.trovacampo.api.config;

import static it.trovacampo.api.dominio.TipoCampionato.AGONISTICA;
import static it.trovacampo.api.dominio.TipoCampionato.SCUOLA_CALCIO;

import it.trovacampo.api.dominio.Campionato;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.service.SocietaService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Carica in MongoDB gli stessi dati di esempio dell'API Node
 * ({@code backend/src/data/societa.ts}), in attesa dell'importazione della
 * banca dati reale. Solo "Certosa Calcio" e "Almas Roma" hanno anagrafica e
 * campionati completi, così resta visibile anche il caso dei dati mancanti.
 *
 * <p>Il caricamento avviene solo a collezione vuota: i campi aggiunti dagli
 * utenti non vengono mai sovrascritti.
 */
@Component
@ConditionalOnProperty(name = "trovacampo.seed.abilitato", havingValue = "true")
public class SeedDati implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDati.class);

    private final SocietaRepository repository;

    public SeedDati(SocietaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        List<Societa> esempi =
                societaDiEsempio().stream().map(SocietaService::aggiornaTestoRicerca).toList();

        repository.saveAll(esempi);
        log.info("Caricate {} società di esempio", esempi.size());
    }

    private List<Societa> societaDiEsempio() {
        return List.of(
                new Societa()
                        .setId("1")
                        .setSiglaSocieta("A.S.D.")
                        .setNomeSocieta("Certosa Calcio")
                        .setComitatoRegionale("LAZIO")
                        .setNomeImpianto("Campo Certosa")
                        .setIndirizzoImpianto("Via della Certosa 12")
                        .setLocalitaImpianto("Roma")
                        .setProvinciaImpianto("RM")
                        .setLat(41.8919)
                        .setLng(12.4863)
                        .setMatricola("4521")
                        .setPresidente("Mario Rossi")
                        .setIndirizzoSede("Via della Certosa 12, Roma")
                        .setTelefono("0612345678")
                        .setEmail("info@certosacalcio.it")
                        .setSitoWeb("www.certosacalcio.it")
                        .setScuolaCalcio(true)
                        .setPrezziScuolaCalcio("180€/anno")
                        .setCampionati(
                                List.of(
                                        new Campionato("Terza Categoria", "B", "LAZIO", AGONISTICA),
                                        new Campionato(
                                                "Scuola Calcio Piccoli Amici",
                                                "-",
                                                "LAZIO",
                                                SCUOLA_CALCIO))),
                new Societa()
                        .setId("2")
                        .setSiglaSocieta("A.S.D.")
                        .setNomeSocieta("Atletico 400 Tor di Pippo")
                        .setComitatoRegionale("LAZIO")
                        .setNomeImpianto("Campo Tor di Pippo")
                        .setIndirizzoImpianto("Via Tor di Pippo 40")
                        .setLocalitaImpianto("Roma")
                        .setProvinciaImpianto("RM")
                        .setLat(41.8567)
                        .setLng(12.5764),
                new Societa()
                        .setId("3")
                        .setSiglaSocieta("A.S.D.")
                        .setNomeSocieta("Almas Roma S.r.l.")
                        .setComitatoRegionale("LAZIO")
                        .setNomeImpianto("Sant'Anna \"A\" (erba)")
                        .setIndirizzoImpianto("Via Demetriade 78")
                        .setLocalitaImpianto("Roma (Tuscolano)")
                        .setProvinciaImpianto("RM")
                        .setLat(41.8697)
                        .setLng(12.5514)
                        .setMatricola("1620")
                        .setPresidente("Massimiliano Di Litta")
                        .setIndirizzoSede("Via Demetriade 78, Roma")
                        .setTelefono("067810020")
                        .setFax("067810020")
                        .setEmail("almas.roma@hotmail.it")
                        .setSitoWeb("www.asdalmasroma.com")
                        .setScuolaCalcio(false)
                        .setCampionati(
                                List.of(
                                        new Campionato("Promozione", "A", "LAZIO", AGONISTICA),
                                        new Campionato(
                                                "Regionale Juniores", "B", "LAZIO", AGONISTICA),
                                        new Campionato(
                                                "Allievi Regionali Eccellenza",
                                                "B",
                                                "LAZIO",
                                                AGONISTICA),
                                        new Campionato(
                                                "Allievi Regionali Fascia B",
                                                "D",
                                                "LAZIO",
                                                AGONISTICA),
                                        new Campionato(
                                                "Regionale Giovanissimi", "C", "LAZIO", AGONISTICA),
                                        new Campionato(
                                                "Giovanissimi Reg. Fascia B",
                                                "D",
                                                "LAZIO",
                                                AGONISTICA),
                                        new Campionato(
                                                "Play Out Allievi Eccellenza",
                                                "B",
                                                "LAZIO",
                                                AGONISTICA),
                                        new Campionato(
                                                "Gare Graduatoria Promozione",
                                                "A",
                                                "LAZIO",
                                                AGONISTICA))),
                new Societa()
                        .setId("4")
                        .setSiglaSocieta("A.S.D.")
                        .setNomeSocieta("Virtus Ostia")
                        .setComitatoRegionale("LAZIO")
                        .setNomeImpianto("Campo Vittorio Paolucci")
                        .setIndirizzoImpianto("Via delle Baleniere 5")
                        .setLocalitaImpianto("Ostia (Roma)")
                        .setProvinciaImpianto("RM")
                        .setLat(41.7328)
                        .setLng(12.2836),
                new Societa()
                        .setId("5")
                        .setSiglaSocieta("A.S.D.")
                        .setNomeSocieta("Nuova Tor Sapienza")
                        .setComitatoRegionale("LAZIO")
                        .setNomeImpianto("Campo Tor Sapienza")
                        .setIndirizzoImpianto("Via Ludovico Pavoni 21")
                        .setLocalitaImpianto("Roma")
                        .setProvinciaImpianto("RM")
                        .setLat(41.8895)
                        .setLng(12.5867));
    }
}
