package it.trovacampo.api.anagrafica;

import it.trovacampo.api.societa.Societa;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dove porta, nella scheda di una società, il collegamento «Accedi a
 * Footballer»: lo dice presenze, e lo dice solo per i club che ne sono clienti.
 *
 * <p>Si chiede a presenze all'apertura della scheda, come le squadre, e non si
 * copia in Mongo con la sincronizzazione dei campi: chi è cliente cambia (un
 * club si attiva, si disattiva) e una copia direbbe il vero solo fino alla
 * sincronizzazione successiva. Una chiamata per scheda aperta, la stessa
 * che le squadre già fanno.
 *
 * <p>Vuoto, e non un errore, in ogni altro caso: società non cliente, società
 * che in presenze non c'è, presenze giù, o presenze che non manda ancora il
 * campo (TrovaCampo si rilascia per conto suo). In tutti la scheda semplicemente
 * non mostra il collegamento.
 */
@Service
public class AccessoSocieta {

    private static final Logger log = LoggerFactory.getLogger(AccessoSocieta.class);

    private final AnagraficaPresenze anagrafica;
    private final SocietaInPresenze inPresenze;

    public AccessoSocieta(AnagraficaPresenze anagrafica, SocietaInPresenze inPresenze) {
        this.anagrafica = anagrafica;
        this.inPresenze = inPresenze;
    }

    public Optional<AnagraficaPresenze.Accesso> di(Societa societa) {
        try {
            Optional<Long> id = inPresenze.idDi(societa);
            if (id.isEmpty()) {
                return Optional.empty();
            }
            AnagraficaPresenze.Scheda scheda = anagrafica.scheda(id.get());
            AnagraficaPresenze.Accesso accesso = scheda == null ? null : scheda.accesso();
            return accesso == null || accesso.alias() == null || accesso.alias().isBlank()
                    ? Optional.empty()
                    : Optional.of(accesso);
        } catch (RuntimeException guasto) {
            log.warn("Accesso di presenze non disponibile: {}", guasto.toString());
            return Optional.empty();
        }
    }
}
