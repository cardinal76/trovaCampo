package it.trovacampo.api.importazione;

import it.trovacampo.api.dominio.Testo;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Le colonne che l'importazione sa leggere, ciascuna con i nomi con cui può
 * comparire nell'intestazione del foglio.
 *
 * <p>Il tracciato non è fissato: i file arrivano da fonti diverse (esportazioni
 * dei comitati, fogli compilati a mano) e ognuna chiama le colonne a modo suo.
 * Il confronto ignora maiuscole, accenti, spazi e punteggiatura, quindi
 * "Società", "SOCIETA'" e "nome_societa" sono la stessa cosa.
 */
enum Colonna {
    NOME_SOCIETA(true, "societa", "nome societa", "denominazione", "denominazione societa", "squadra", "club"),
    NOME_IMPIANTO(true, "impianto", "nome impianto", "campo", "nome campo", "campo di gioco", "stadio"),
    INDIRIZZO(true, "indirizzo", "indirizzo impianto", "indirizzo campo", "via"),
    LOCALITA(false, "localita", "localita impianto", "comune", "citta", "paese"),
    PROVINCIA(false, "provincia", "prov", "sigla provincia"),
    LAT(false, "lat", "latitudine", "latitude"),
    LNG(false, "lng", "lon", "long", "longitudine", "longitude");

    private final boolean obbligatoria;
    private final List<String> nomi;

    Colonna(boolean obbligatoria, String... nomi) {
        this.obbligatoria = obbligatoria;
        this.nomi = Arrays.stream(nomi).map(Colonna::chiave).toList();
    }

    boolean obbligatoria() {
        return obbligatoria;
    }

    /** Il primo nome, quello da suggerire nei messaggi d'errore. */
    String nomePrincipale() {
        return nomi.getFirst();
    }

    static Optional<Colonna> perIntestazione(String intestazione) {
        String chiave = chiave(intestazione);
        return Arrays.stream(values()).filter(colonna -> colonna.nomi.contains(chiave)).findFirst();
    }

    /** "Nome Società (campo)" diventa "nomesocietacampo". */
    private static String chiave(String testo) {
        return Testo.normalizza(testo).replaceAll("[^a-z0-9]", "");
    }
}
