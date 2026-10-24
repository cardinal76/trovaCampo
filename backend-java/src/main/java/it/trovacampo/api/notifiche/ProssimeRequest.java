package it.trovacampo.api.notifiche;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Quello che il browser manda per sapere le sue prossime partite: le stesse
 * scelte dell'iscrizione ({@link IscrizioneRequest}), con gli stessi limiti,
 * prese dal telefono. Così la pagina funziona anche dove le notifiche non
 * sono accese, e non mostra mai le scelte di qualcun altro: il server
 * risponde solo su quello che gli si manda.
 *
 * @param squadre le chiavi delle squadre seguite; vuoto se non se ne segue nessuna
 * @param posizione solo con l'avviso delle partite vicine acceso; null senza
 * @param raggioKm per le partite vicine; senza, quello predefinito
 * @param endpoint l'iscrizione push di questo browser, se c'è: solo per dire
 *     quali avvisi sono già arrivati. Non si salva e non si cerca altro con lui
 */
public record ProssimeRequest(
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
        @Valid IscrizioneRequest.Posizione posizione,
        @Min(value = 1, message = "Raggio di almeno 1 km")
                @Max(
                        value = IscrizioniService.MASSIMO_RAGGIO_KM,
                        message = "Raggio di al massimo " + IscrizioniService.MASSIMO_RAGGIO_KM + " km")
                Integer raggioKm,
        @Size(max = IscrizioniService.MASSIMO_ENDPOINT, message = "Endpoint troppo lungo") String endpoint) {}
