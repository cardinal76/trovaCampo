package it.trovacampo.api.indirizzi;

import java.util.ArrayList;
import java.util.List;

/**
 * Un CSV come lo scrivono i portali open data: separatore a scelta, campi
 * fra virgolette con virgole e a capo dentro, virgolette raddoppiate, BOM in
 * testa. Niente libreria per una funzione di trenta righe.
 */
final class LettoreCsv {

    private LettoreCsv() {}

    static List<List<String>> leggi(String testo, char separatore) {
        List<List<String>> righe = new ArrayList<>();
        List<String> riga = new ArrayList<>();
        StringBuilder campo = new StringBuilder();
        boolean fraVirgolette = false;
        int inizio = !testo.isEmpty() && testo.charAt(0) == '﻿' ? 1 : 0;

        for (int i = inizio; i < testo.length(); i++) {
            char c = testo.charAt(i);
            if (fraVirgolette) {
                if (c == '"' && i + 1 < testo.length() && testo.charAt(i + 1) == '"') {
                    campo.append('"');
                    i++;
                } else if (c == '"') {
                    fraVirgolette = false;
                } else {
                    campo.append(c);
                }
            } else if (c == '"' && campo.isEmpty()) {
                fraVirgolette = true;
            } else if (c == separatore) {
                riga.add(campo.toString());
                campo.setLength(0);
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < testo.length() && testo.charAt(i + 1) == '\n') {
                    i++;
                }
                riga.add(campo.toString());
                campo.setLength(0);
                if (!(riga.size() == 1 && riga.get(0).isEmpty())) {
                    righe.add(riga);
                }
                riga = new ArrayList<>();
            } else {
                campo.append(c);
            }
        }
        if (!campo.isEmpty() || !riga.isEmpty()) {
            riga.add(campo.toString());
            righe.add(riga);
        }
        return righe;
    }
}
