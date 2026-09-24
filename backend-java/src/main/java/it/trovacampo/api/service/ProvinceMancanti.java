package it.trovacampo.api.service;

import it.trovacampo.api.dominio.ProvinciaDalComune;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.repository.SocietaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

/**
 * All'avvio completa la provincia dei campi che ne sono senza, ricavandola
 * dal comune.
 *
 * <p>Importazioni e sincronizzazione la ricavano già da sole, ma i campi
 * arrivati prima non l'hanno, e senza aspettare il prossimo giro di
 * presenze devono comparire subito nel filtro per provincia. Tocca solo le
 * province vuote: una scritta a mano o da un file resta com'è. A regime non
 * trova niente da fare, e con qualche migliaio di campi il giro costa poco.
 */
@Component
@ConditionalOnProperty(name = "trovacampo.province.completamento", havingValue = "true")
public class ProvinceMancanti implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ProvinceMancanti.class);

    private final SocietaRepository repository;
    private final MongoTemplate mongo;

    public ProvinceMancanti(SocietaRepository repository, MongoTemplate mongo) {
        this.repository = repository;
        this.mongo = mongo;
    }

    @Override
    public void run(String... args) {
        int completate = 0;
        for (Societa societa : repository.findAll()) {
            if (!vuota(societa.getProvinciaImpianto())) {
                continue;
            }
            String sigla = ProvinciaDalComune.sigla(societa.getLocalitaImpianto());
            if (sigla.isEmpty()) {
                continue;
            }
            // Solo la provincia, non il documento intero: intanto la
            // geocodifica automatica può star scrivendo le coordinate dello
            // stesso campo, e un salvataggio completo le cancellerebbe.
            mongo.updateFirst(
                    Query.query(Criteria.where("_id").is(societa.getId())),
                    Update.update("provinciaImpianto", sigla),
                    Societa.class);
            completate++;
        }
        if (completate > 0) {
            log.info("Provincia ricavata dal comune per {} campi", completate);
        }
    }

    private static boolean vuota(String provincia) {
        return provincia == null || provincia.isBlank();
    }
}
