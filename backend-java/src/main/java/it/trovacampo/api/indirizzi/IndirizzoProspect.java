package it.trovacampo.api.indirizzi;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * L'esito della ricerca dell'indirizzo per una società di {@code prospect},
 * con la stessa chiave (il codice fiscale). Sta in una collection sua,
 * {@code indirizzo_prospect}, così l'arricchimento non scrive nei documenti
 * del registro: chi li legge li unisce per codice fiscale.
 *
 * <p>Una società cercata e non trovata ha la riga con {@code fonte} nulla:
 * il report la conta fra le mancanti, e l'arricchimento successivo la
 * ritenta solo se glielo si chiede.
 */
@Document(collection = "indirizzo_prospect")
public record IndirizzoProspect(
        @Id String codiceFiscale,
        String denominazione,
        String comuneSede,
        String indirizzo,
        String comune,
        Double lat,
        Double lng,
        Fonte fonte,
        Confidenza confidenza,
        Double punteggio,
        String abbinatoA,
        String impianto,
        String attribuzione,
        boolean conRete,
        Instant cercatoIl) {

    static IndirizzoProspect di(
            LettoreProspect.SocietaProspect societa, IndirizzoMigliore trovato, boolean conRete, Instant adesso) {
        if (trovato == null) {
            return new IndirizzoProspect(
                    societa.codiceFiscale(), societa.denominazione(), societa.comune(), null, null, null, null,
                    null, null, null, null, null, null, conRete, adesso);
        }
        return new IndirizzoProspect(
                societa.codiceFiscale(), societa.denominazione(), societa.comune(), trovato.indirizzo(),
                trovato.comune(), trovato.lat(), trovato.lng(), trovato.fonte(), trovato.confidenza(),
                trovato.punteggio(), trovato.abbinatoA(), trovato.impianto(), trovato.attribuzione(), conRete,
                adesso);
    }

    public boolean trovato() {
        return fonte != null;
    }
}
