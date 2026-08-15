package it.trovacampo.api.web;

public class SocietaNonTrovataException extends RuntimeException {

    public SocietaNonTrovataException() {
        super("Società non trovata");
    }
}
