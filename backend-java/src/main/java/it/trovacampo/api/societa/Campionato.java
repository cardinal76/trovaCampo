package it.trovacampo.api.societa;


/** Campionato a cui una società partecipa (Funzione 3). */
public record Campionato(String descrizione, String girone, String comitato, TipoCampionato tipo) {}
