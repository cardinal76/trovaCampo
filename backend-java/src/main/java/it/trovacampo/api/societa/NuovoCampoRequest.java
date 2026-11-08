package it.trovacampo.api.societa;

import jakarta.validation.constraints.NotBlank;

/** Campo segnalato dall'app con la Funzione 1. */
public record NuovoCampoRequest(
        @NotBlank(message = "nomeSocieta è obbligatorio") String nomeSocieta,
        @NotBlank(message = "nomeImpianto è obbligatorio") String nomeImpianto,
        @NotBlank(message = "indirizzoImpianto è obbligatorio") String indirizzoImpianto) {}
