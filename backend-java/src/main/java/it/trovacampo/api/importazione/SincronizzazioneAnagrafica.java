package it.trovacampo.api.importazione;

import it.trovacampo.api.anagrafica.AnagraficaPresenze;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Porta in TrovaCampo i campi dell'anagrafica di presenze.
 *
 * <p>Ogni coppia (società, campo dove gioca in casa) diventa una riga, come
 * quelle di un file: stessa chiave, stesse regole. Una società già presente
 * con lo stesso campo si aggiorna e prende il legame con l'anagrafica, da cui
 * la scheda legge squadre e campionati; una nuova si inserisce. Niente si
 * cancella: i campi inseriti a mano o da un file restano.
 *
 * <p>Le coordinate di presenze, quando ci sono, valgono come quelle di un
 * file; quando mancano restano quelle che TrovaCampo aveva già, se il campo
 * non si è spostato, altrimenti le cerca la geocodifica automatica.
 */
@Service
public class SincronizzazioneAnagrafica {

    private static final Logger log = LoggerFactory.getLogger(SincronizzazioneAnagrafica.class);

    private final AnagraficaPresenze anagrafica;
    private final ImportazioneService importazione;

    public SincronizzazioneAnagrafica(
            AnagraficaPresenze anagrafica, ImportazioneService importazione) {
        this.anagrafica = anagrafica;
        this.importazione = importazione;
    }

    public EsitoImportazione sincronizza(boolean prova) {
        List<RigaExcel> righe = new ArrayList<>();
        List<Scarto> scarti = new ArrayList<>();
        int numero = 0;

        for (AnagraficaPresenze.Impianto impianto : anagrafica.impianti()) {
            if (impianto.societa() == null) {
                continue;
            }
            for (AnagraficaPresenze.Riferimento societa : impianto.societa()) {
                numero++;
                if (impianto.indirizzo() == null || impianto.indirizzo().isBlank()) {
                    scarti.add(
                            new Scarto(
                                    numero,
                                    societa.denominazione() + ": il campo " + impianto.nome()
                                            + " non ha indirizzo"));
                    continue;
                }
                righe.add(
                        new RigaExcel(
                                numero,
                                societa.denominazione().strip(),
                                impianto.nome().strip(),
                                impianto.indirizzo().strip().replaceAll("\\s+", " "),
                                impianto.comune() == null ? "" : impianto.comune().strip(),
                                "",
                                impianto.lat(),
                                impianto.lng(),
                                societa.id(),
                                impianto.id()));
            }
        }

        EsitoImportazione esito =
                importazione.importaRighe(new LettoreExcel.Lettura(righe, scarti, List.of()), prova);
        if (!prova) {
            log.info(
                    "Anagrafica di presenze: {} inserite, {} aggiornate, {} già uguali",
                    esito.inserite(),
                    esito.aggiornate(),
                    esito.invariate());
        }
        return esito;
    }
}
