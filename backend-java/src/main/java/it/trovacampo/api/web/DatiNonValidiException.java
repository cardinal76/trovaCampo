package it.trovacampo.api.web;

/** Dati coerenti campo per campo ma non fra loro, es. latitudine senza longitudine. */
public class DatiNonValidiException extends RuntimeException {

    public DatiNonValidiException(String messaggio) {
        super(messaggio);
    }
}
