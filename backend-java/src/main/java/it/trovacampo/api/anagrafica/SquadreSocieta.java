package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Le squadre di una società con il campionato di ognuna, dall'anagrafica di
 * presenze. Una società che non viene da lì (inserita a mano, o da un file)
 * non ne ha.
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
     */
    public record Squadra(
            String campionato,
            String ente,
            String stagione,
            String girone,
            String squadra,
            boolean fuoriClassifica,
            String campo) {}

    private final AnagraficaPresenze anagrafica;

    public SquadreSocieta(AnagraficaPresenze anagrafica) {
        this.anagrafica = anagrafica;
    }

    public List<Squadra> di(Societa societa) {
        if (societa.getAnagraficaSocietaId() == null) {
            return List.of();
        }
        AnagraficaPresenze.Scheda scheda = anagrafica.scheda(societa.getAnagraficaSocietaId());
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
                                        squadra.campoId() == null ? null : campi.get(squadra.campoId())))
                .toList();
    }
}
