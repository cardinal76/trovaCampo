package it.trovacampo.api.segnalazioni;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Il form «Hai dati da rettificare?». {@code sito} e' una casella nascosta
 * che una persona non vede e non riempie: se arriva piena e' un programma, e
 * la richiesta si scarta senza dirlo.
 *
 * @param societaId la scheda da cui si e' partiti, se c'e'
 * @param attuale cosa c'e' scritto adesso (facoltativo)
 * @param corretto cosa dovrebbe esserci scritto
 * @param contatto email per rispondere (facoltativa)
 */
public record SegnalazioneRequest(
        @NotNull(message = "Manca il tipo di rettifica") TipoRettifica tipo,
        @Size(max = 60, message = "Identificativo non valido") String societaId,
        @Size(max = 200, message = "Testo troppo lungo") String attuale,
        @NotBlank(message = "Scrivi il dato corretto") @Size(max = 500, message = "Testo troppo lungo")
                String corretto,
        @Email(message = "Email non valida") @Size(max = 150, message = "Email troppo lunga")
                String contatto,
        @Size(max = 100) String sito) {}
