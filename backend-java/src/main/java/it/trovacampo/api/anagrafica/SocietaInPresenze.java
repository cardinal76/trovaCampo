package it.trovacampo.api.anagrafica;

import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.dominio.Testo;
import it.trovacampo.api.repository.SocietaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Quale società dell'anagrafica di presenze è una società di TrovaCampo.
 *
 * <p>Una società già legata (dalla sincronizzazione) si legge direttamente.
 * Una non ancora legata, arrivata da un file o inserita a mano, si cerca per
 * nome: se presenze ne ha una sola con quel nome, o una con esattamente lo
 * stesso nome, il legame si salva e da lì in poi vale come quello della
 * sincronizzazione. Se il nome è ambiguo non si indovina.
 *
 * <p>Sta per conto suo perché il legame serve a più di una scheda — le
 * squadre con i loro campionati e gli ultimi risultati — e indovinare il
 * nome in due modi diversi vorrebbe dire due schede che parlano di due
 * società.
 */
@Service
public class SocietaInPresenze {

    private final AnagraficaPresenze anagrafica;
    private final SocietaRepository repository;

    public SocietaInPresenze(AnagraficaPresenze anagrafica, SocietaRepository repository) {
        this.anagrafica = anagrafica;
        this.repository = repository;
    }

    /** L'id in presenze, vuoto per una società che lì non c'è o il cui nome è ambiguo. */
    public Optional<Long> idDi(Societa societa) {
        Long legata = societa.getAnagraficaSocietaId();
        if (legata != null) {
            return Optional.of(legata);
        }
        Optional<Long> trovata = cercaPerNome(societa.getNomeSocieta());
        trovata.ifPresent(id -> repository.save(societa.setAnagraficaSocietaId(id)));
        return trovata;
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
