package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.dominio.Testo;
import it.trovacampo.api.notifiche.ChiaveSquadra;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Le squadre di una società con il campionato di ognuna, dall'anagrafica di
 * presenze.
 *
 * <p>Una società già legata all'anagrafica (dalla sincronizzazione) si legge
 * direttamente. Una non ancora legata, arrivata da un file o inserita a mano,
 * si cerca per nome: se presenze ne ha una sola con quel nome, o una con
 * esattamente lo stesso nome, il legame si salva e da lì in poi vale come
 * quello della sincronizzazione. Se il nome è ambiguo non si indovina.
 */
@Service
public class SquadreSocieta {

    /**
     * Una squadra della società.
     *
     * @param ente "Regionali" per il Comitato, il nome della delegazione per i provinciali
     * @param girone vuoto finché i gironi non escono
     * @param squadra vuota per la prima squadra, "B" per la seconda
     * @param campo dove gioca in casa, se si sa
     * @param chiave come la riconoscono le notifiche di chi la segue: vedi {@link ChiaveSquadra}
     */
    public record Squadra(
            String campionato,
            String ente,
            String stagione,
            String girone,
            String squadra,
            boolean fuoriClassifica,
            String campo,
            String chiave) {}

    private final AnagraficaPresenze anagrafica;
    private final SocietaRepository repository;

    public SquadreSocieta(AnagraficaPresenze anagrafica, SocietaRepository repository) {
        this.anagrafica = anagrafica;
        this.repository = repository;
    }

    public List<Squadra> di(Societa societa) {
        Optional<Long> id = Optional.ofNullable(societa.getAnagraficaSocietaId());
        if (id.isEmpty()) {
            id = cercaPerNome(societa.getNomeSocieta());
            id.ifPresent(trovato -> repository.save(societa.setAnagraficaSocietaId(trovato)));
        }
        if (id.isEmpty()) {
            return List.of();
        }
        long societaPresenze = id.get();
        AnagraficaPresenze.Scheda scheda = anagrafica.scheda(societaPresenze);
        if (scheda == null || scheda.squadre() == null) {
            return List.of();
        }
        Map<Long, String> campi =
                scheda.campi() == null
                        ? Map.of()
                        : scheda.campi().stream()
                                .collect(
                                        Collectors.toMap(
                                                AnagraficaPresenze.Campo::id,
                                                AnagraficaPresenze.Campo::nome,
                                                (a, b) -> a));
        return scheda.squadre().stream()
                .map(
                        squadra ->
                                new Squadra(
                                        squadra.campionato(),
                                        squadra.ente(),
                                        squadra.stagione(),
                                        squadra.girone(),
                                        Objects.requireNonNullElse(squadra.squadra(), ""),
                                        squadra.fuoriClassifica(),
                                        squadra.campoId() == null ? null : campi.get(squadra.campoId()),
                                        ChiaveSquadra.di(
                                                societaPresenze,
                                                squadra.campionato(),
                                                squadra.ente(),
                                                squadra.squadra())))
                .toList();
    }

    private Optional<Long> cercaPerNome(String nome) {
        if (nome == null || nome.isBlank()) {
            return Optional.empty();
        }
        List<AnagraficaPresenze.Riferimento> trovate = anagrafica.cerca(nome.strip());
        String cercato = compatta(nome);
        Optional<AnagraficaPresenze.Riferimento> uguale =
                trovate.stream().filter(una -> compatta(una.denominazione()).equals(cercato)).findFirst();
        if (uguale.isPresent()) {
            return uguale.map(AnagraficaPresenze.Riferimento::id);
        }
        // Presenze cerca anche fra i nomi con cui la società è stata letta:
        // una sola risposta è lei, scritta in un altro modo.
        return trovate.size() == 1 ? Optional.of(trovate.getFirst().id()) : Optional.empty();
    }

    /** "A.S.D. Certosa  Calcio" e "asd certosa calcio" sono lo stesso nome. */
    private static String compatta(String testo) {
        return Testo.normalizza(testo).replaceAll("[^a-z0-9]+", "");
    }
}
