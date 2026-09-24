package it.trovacampo.api.notifiche;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Basta l'endpoint: è lui a identificare l'iscrizione, e lo conosce solo quel browser. */
public record CancellazioneRequest(
        @NotBlank(message = "Manca l'endpoint dell'iscrizione")
                @Size(max = IscrizioniService.MASSIMO_ENDPOINT, message = "Endpoint troppo lungo")
                String endpoint) {}
