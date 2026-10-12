package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.notifiche.ChiaveSquadra;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Le squadre di una società con il campionato di ognuna, dall'anagrafica di
 * presenze.
 *
 * <p>Quale società sia, in presenze, lo dice {@link SocietaInPresenze}: lo
 * stesso legame che cercano gli ultimi risultati.
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
     * @param risultatiDaComunicato presente solo mentre il portale LND non risponde a
     *     presenze: la scheda avvisa che i risultati sono fermi a quel comunicato.
     *     Passa così com'è: lo decide presenze, che sa se il portale risponde
     */
    public record Squadra(
            String campionato,
            String ente,
            String stagione,
            String girone,
            String squadra,
            boolean fuoriClassifica,
            String campo,
            String chiave,
            AnagraficaPresenze.RisultatiDaComunicato risultatiDaComunicato) {

        /** Senza l'avviso sui risultati, per chi non ne ha. */
        public Squadra(
                String campionato,
                String ente,
                String stagione,
                String girone,
                String squadra,
                boolean fuoriClassifica,
                String campo,
                String chiave) {
            this(campionato, ente, stagione, girone, squadra, fuoriClassifica, campo, chiave, null);
        }
    }

    private final AnagraficaPresenze anagrafica;
    private final SocietaInPresenze inPresenze;

    public SquadreSocieta(AnagraficaPresenze anagrafica, SocietaInPresenze inPresenze) {
        this.anagrafica = anagrafica;
        this.inPresenze = inPresenze;
    }

    public List<Squadra> di(Societa societa) {
        Optional<Long> id = inPresenze.idDi(societa);
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
                                                squadra.squadra()),
                                        squadra.risultatiDaComunicato()))
                .toList();
    }
}
