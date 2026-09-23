package it.trovacampo.api.importazione;

import java.util.List;

/**
 * Il resoconto restituito a chi carica il file.
 *
 * @param prova vero se non è stato salvato niente: il resoconto dice cosa
 *     succederebbe
 * @param righeLette le righe non vuote trovate sotto l'intestazione
 * @param daGeocodificare quante società, fra quelle inserite o aggiornate, non
 *     hanno ancora coordinate: le cerca la geocodifica automatica, una al
 *     secondo
 * @param colonneIgnorate le intestazioni che non corrispondono a nessuna
 *     colonna nota, utili per accorgersi di un nome scritto diversamente
 */
public record EsitoImportazione(
        boolean prova,
        int righeLette,
        int inserite,
        int aggiornate,
        int invariate,
        List<Scarto> scartate,
        int daGeocodificare,
        List<String> colonneIgnorate) {}
