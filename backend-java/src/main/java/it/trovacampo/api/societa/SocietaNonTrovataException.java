package it.trovacampo.api.societa;


public class SocietaNonTrovataException extends RuntimeException {

    public SocietaNonTrovataException() {
        super("Società non trovata");
    }
}
