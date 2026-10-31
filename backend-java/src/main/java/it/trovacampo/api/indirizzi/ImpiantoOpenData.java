package it.trovacampo.api.indirizzi;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Un impianto sportivo con campi da calcio, da un censimento in open data.
 * Sta in una collection a parte, {@code impianto_open_data}, perché non è un
 * campo di TrovaCampo: serve solo a trovare l'indirizzo di una società.
 *
 * <p>Solo dati dell'impianto: nessun gestore, concessionario o referente.
 *
 * @param id fonte + nome, comune e indirizzo normalizzati: reimportare lo
 *     stesso file sovrascrive, non duplica
 * @param fonte la sigla della fonte, per esempio {@code cmrc-censimento}
 * @param licenza la licenza del dataset, per esempio "CC0 1.0"
 * @param sport le discipline di calcio dichiarate (Calcio-11, Calcio-5...)
 * @param lat nulla quando il dataset non ha coordinate: allora le dà la
 *     geocodifica dell'indirizzo, al momento dell'abbinamento
 * @param attribuzione la riga di attribuzione del dataset da cui viene (per
 *     il Veneto, il comune); nulla negli impianti importati prima che ci
 *     fosse, che usano quella della fonte
 */
@Document(collection = "impianto_open_data")
public record ImpiantoOpenData(
        @Id String id,
        String fonte,
        String licenza,
        String nome,
        String comune,
        String municipio,
        String indirizzo,
        List<String> sport,
        Double lat,
        Double lng,
        Instant importatoIl,
        String attribuzione) {

    /** Un campo a 11: quello che serve a una società che fa un campionato LND. */
    public boolean haCalcioA11() {
        return sport != null && sport.stream().anyMatch(disciplina -> disciplina.contains("11"));
    }

    /** La riga da mostrare accanto all'indirizzo, come chiede la licenza del dataset. */
    public String attribuzioneDaMostrare() {
        if (attribuzione != null && !attribuzione.isBlank()) {
            return attribuzione;
        }
        return FonteOpenData.daSigla(fonte)
                .map(FonteOpenData::attribuzione)
                .orElseGet(() -> fonte + (licenza == null ? "" : " (" + licenza + ")"));
    }
}
