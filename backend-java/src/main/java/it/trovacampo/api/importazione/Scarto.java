package it.trovacampo.api.importazione;

/** Una riga del file non importata, con il numero che si vede aprendo il file. */
public record Scarto(int riga, String motivo) {}
