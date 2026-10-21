package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.dominio.Testo;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Le società di presenze che in TrovaCampo non ci sono, perché del loro campo
 * non si sa niente.
 *
 * <p>Una scheda di TrovaCampo è una coppia società-campo: una società senza
 * campo non ha una scheda, e dall'elenco non la si vede mancare. Il campo lo
 * pubblica il comitato nel programma gare, e per qualcuna a ottobre non l'ha
 * ancora fatto. Qui si vede quali sono, per aggiungerne il campo a mano.
 *
 * <p>Una società esce dall'elenco appena ha una scheda: collegata a presenze
 * (lo stesso id) o anche solo con lo stesso nome, perché quella aggiunta a
 * mano prima di oggi il collegamento non ce l'ha.
 */
@Service
public class SocietaSenzaCampo {

    /** Una società da completare, con quello che serve a precompilarne la scheda. */
    public record Voce(Long anagraficaSocietaId, String denominazione, String logoUrl) {}

    private final AnagraficaPresenze presenze;
    private final SocietaRepository repository;

    public SocietaSenzaCampo(AnagraficaPresenze presenze, SocietaRepository repository) {
        this.presenze = presenze;
        this.repository = repository;
    }

    public List<Voce> elenco() {
        Set<Long> collegate = new HashSet<>();
        Set<String> nomi = new HashSet<>();
        for (Societa scheda : repository.nomiECollegamenti()) {
            if (scheda.getAnagraficaSocietaId() != null) {
                collegate.add(scheda.getAnagraficaSocietaId());
            }
            nomi.add(Testo.normalizza(scheda.getNomeSocieta()));
        }
        return presenze.societa().stream()
                .filter(s -> s.id() != null && s.campo() == null)
                .filter(s -> !collegate.contains(s.id()))
                .filter(s -> !nomi.contains(Testo.normalizza(s.denominazione())))
                .map(s -> new Voce(s.id(), s.denominazione(), s.logoUrl()))
                .sorted(Comparator.comparing(Voce::denominazione, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();
    }
}
