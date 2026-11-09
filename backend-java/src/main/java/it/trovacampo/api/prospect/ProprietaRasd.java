package it.trovacampo.api.prospect;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Il Registro nazionale (RASD) di Sport e Salute ({@code trovacampo.prospect.rasd.*}):
 * l'indirizzo, chi siamo, il minimo fra due richieste nel client HTTP, il ritmo
 * dell'importazione e per quanti giorni un dettaglio letto resta valido.
 */
@ConfigurationProperties("trovacampo.prospect.rasd")
@Validated
public record ProprietaRasd(
        @NotBlank String url,
        @NotBlank String userAgent,
        @Positive long intervalloMs,
        @DefaultValue("30") @Positive int dettaglioValidoGiorni,
        @DefaultValue Ritmo ritmo) {

    /** Il ritmo: la priorita' e' non farsi bloccare, non la velocita'. */
    public record Ritmo(
            @DefaultValue("3000") long pausaMs,
            @DefaultValue("1000") long jitterMs,
            @DefaultValue("22:00-07:00") String finestra,
            @DefaultValue("Europe/Rome") String fuso,
            @DefaultValue("7000") int tettoGiornaliero) {}
}
