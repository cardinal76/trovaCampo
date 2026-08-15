package it.trovacampo.api.service;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.dominio.Testo;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.web.NuovoCampoRequest;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class SocietaService {

    private final SocietaRepository repository;
    private final Geocoding geocoding;

    public SocietaService(SocietaRepository repository, Geocoding geocoding) {
        this.repository = repository;
        this.geocoding = geocoding;
    }

    /** Funzione 1: ricerca per nome società, nome impianto, indirizzo o località. */
    public List<Societa> cerca(String nome) {
        String termine = Testo.normalizza(nome);

        if (termine.isEmpty()) {
            return List.of();
        }

        return repository.cercaPerTesto(Testo.perRegex(termine));
    }

    /** Funzioni 2 e 3: anagrafica e campionati della società. */
    public Optional<Societa> perId(String id) {
        return repository.findById(id);
    }

    /**
     * Inserisce un campo segnalato dagli utenti. Se la geocodifica non
     * riconosce l'indirizzo il campo viene salvato lo stesso, senza
     * coordinate: comparirà in ricerca ma non come pin sulla mappa.
     */
    public Societa inserisci(NuovoCampoRequest richiesta) {
        Societa societa =
                new Societa()
                        .setSiglaSocieta("")
                        .setNomeSocieta(richiesta.nomeSocieta().trim())
                        .setComitatoRegionale("")
                        .setNomeImpianto(richiesta.nomeImpianto().trim())
                        .setIndirizzoImpianto(richiesta.indirizzoImpianto().trim())
                        .setLocalitaImpianto("")
                        .setProvinciaImpianto("");

        geocoding
                .geocodifica(societa.getIndirizzoImpianto())
                .ifPresent(
                        coordinate -> societa.setLat(coordinate.lat()).setLng(coordinate.lng()));

        return repository.save(aggiornaTestoRicerca(societa));
    }

    /** Ricalcola la copia normalizzata dei campi su cui lavora la ricerca. */
    public static Societa aggiornaTestoRicerca(Societa societa) {
        String testo =
                Stream.of(
                                societa.getNomeSocieta(),
                                societa.getNomeImpianto(),
                                societa.getIndirizzoImpianto(),
                                societa.getLocalitaImpianto())
                        .map(Testo::normalizza)
                        .filter(parte -> !parte.isEmpty())
                        .reduce((a, b) -> a + " | " + b)
                        .orElse("");

        return societa.setTestoRicerca(testo);
    }
}
