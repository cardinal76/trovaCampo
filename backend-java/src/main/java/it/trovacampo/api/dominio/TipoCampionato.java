package it.trovacampo.api.dominio;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Distinzione fra scuola calcio e settore agonistico richiesta dalla
 * Funzione 3. I valori JSON restano quelli usati dall'app Expo esistente.
 */
public enum TipoCampionato {

    @JsonProperty("ScuolaCalcio")
    SCUOLA_CALCIO,

    @JsonProperty("Agonistica")
    AGONISTICA
}
