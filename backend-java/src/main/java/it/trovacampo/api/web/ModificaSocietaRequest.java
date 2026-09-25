package it.trovacampo.api.web;

import it.trovacampo.api.dominio.Campionato;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * La scheda completa di una società, come la manda chi amministra dalla
 * pagina di modifica. Sostituisce quella salvata: un campo lasciato vuoto
 * viene cancellato, perché nel modulo si vedono tutti.
 *
 * <p>Latitudine e longitudine sono facoltative e vanno insieme: se mancano o
 * restano quelle di prima mentre l'indirizzo cambia, le ricalcola la
 * geocodifica automatica.
 *
 * <p>Lo stemma è l'eccezione alla regola del modulo completo: se manca
 * ({@code null}, come da un'app di prima che il campo esistesse) resta quello
 * salvato; vuoto lo toglie. Deve essere un indirizzo https, perché la pagina
 * lo mostra così com'è.
 */
public record ModificaSocietaRequest(
        @Size(max = 30, message = "sigla troppo lunga") String siglaSocieta,
        @NotBlank(message = "nomeSocieta è obbligatorio")
                @Size(max = 150, message = "nomeSocieta troppo lungo")
                String nomeSocieta,
        @Size(max = 60, message = "comitatoRegionale troppo lungo") String comitatoRegionale,
        @NotBlank(message = "nomeImpianto è obbligatorio")
                @Size(max = 150, message = "nomeImpianto troppo lungo")
                String nomeImpianto,
        @NotBlank(message = "indirizzoImpianto è obbligatorio")
                @Size(max = 200, message = "indirizzoImpianto troppo lungo")
                String indirizzoImpianto,
        @Size(max = 100, message = "localitaImpianto troppo lunga") String localitaImpianto,
        @Size(max = 10, message = "provinciaImpianto troppo lunga") String provinciaImpianto,
        @DecimalMin(value = "-90", message = "latitudine fuori scala")
                @DecimalMax(value = "90", message = "latitudine fuori scala")
                Double lat,
        @DecimalMin(value = "-180", message = "longitudine fuori scala")
                @DecimalMax(value = "180", message = "longitudine fuori scala")
                Double lng,
        @Size(max = 30, message = "matricola troppo lunga") String matricola,
        @Size(max = 100, message = "presidente troppo lungo") String presidente,
        @Size(max = 200, message = "indirizzoSede troppo lungo") String indirizzoSede,
        @Size(max = 40, message = "telefono troppo lungo") String telefono,
        @Size(max = 40, message = "fax troppo lungo") String fax,
        @Email(message = "email non valida") @Size(max = 120, message = "email troppo lunga") String email,
        @Size(max = 200, message = "sitoWeb troppo lungo") String sitoWeb,
        Boolean scuolaCalcio,
        @Size(max = 200, message = "prezziScuolaCalcio troppo lungo") String prezziScuolaCalcio,
        @Size(max = 50, message = "troppi campionati") List<Campionato> campionati,
        @Size(max = 500, message = "logoUrl troppo lungo")
                @Pattern(regexp = "^$|^https://\\S+$", message = "lo stemma deve essere un indirizzo https")
                String logoUrl) {}
