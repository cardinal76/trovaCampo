package it.trovacampo.api.segnalazioni;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Una richiesta di rettifica arrivata dal sito, in attesa che qualcuno la guardi. */
@Document(collection = "segnalazioni")
public record Segnalazione(
        @Id String id,
        TipoRettifica tipo,
        String societaId,
        String attuale,
        String corretto,
        String contatto,
        Instant ricevutaIl,
        boolean gestita) {}
