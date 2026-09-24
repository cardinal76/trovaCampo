package it.trovacampo.api.notifiche;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Quello che il telefono manda accendendo, cambiando o rinfrescando le
 * notifiche: l'iscrizione push del browser (come la dà
 * {@code PushSubscription.toJSON()}) e le preferenze.
 *
 * @param posizione obbligatoria con l'avviso delle partite vicine, ignorata senza
 */
public record IscrizioneRequest(
        @NotBlank(message = "Manca l'endpoint dell'iscrizione")
                @Size(max = IscrizioniService.MASSIMO_ENDPOINT, message = "Endpoint troppo lungo")
                String endpoint,
        @NotNull(message = "Mancano le chiavi dell'iscrizione") @Valid Chiavi keys,
        @NotNull(message = "Manca l'elenco delle squadre seguite")
                @Size(
                        max = IscrizioniService.MASSIMO_SQUADRE,
                        message = "Al massimo " + IscrizioniService.MASSIMO_SQUADRE + " squadre seguite")
                List<
                                @NotNull
                                @Pattern(
                                        regexp = "\\d{1,12}\\|[a-z0-9 ]{0,120}\\|[a-z0-9 ]{0,120}\\|[a-z0-9 ]{0,10}",
                                        message = "Squadra seguita non valida")
                                String>
                        squadre,
        boolean avvisoSquadre,
        boolean avvisoVicino,
        @Valid Posizione posizione,
        @Min(value = 1, message = "Raggio di almeno 1 km")
                @Max(
                        value = IscrizioniService.MASSIMO_RAGGIO_KM,
                        message = "Raggio di al massimo " + IscrizioniService.MASSIMO_RAGGIO_KM + " km")
                Integer raggioKm) {

    /** Le chiavi del browser, in base64url: i nomi sono quelli di PushSubscription.toJSON(). */
    public record Chiavi(
            @NotBlank(message = "Manca la chiave p256dh") @Size(max = 200, message = "Chiave p256dh non valida")
                    String p256dh,
            @NotBlank(message = "Manca la chiave auth") @Size(max = 100, message = "Chiave auth non valida")
                    String auth) {}

    public record Posizione(
            @NotNull(message = "Manca la latitudine")
                    @DecimalMin(value = "-90", message = "Latitudine non valida")
                    @DecimalMax(value = "90", message = "Latitudine non valida")
                    Double lat,
            @NotNull(message = "Manca la longitudine")
                    @DecimalMin(value = "-180", message = "Longitudine non valida")
                    @DecimalMax(value = "180", message = "Longitudine non valida")
                    Double lng) {}
}
