package it.trovacampo.api.indirizzi;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Un CSV come lo scrivono i portali open data: separatore a scelta, campi
 * fra virgolette con virgole e a capo dentro, virgolette raddoppiate, BOM in
 * testa. Niente libreria per una funzione di trenta righe.
 */
final class LettoreCsv {

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private LettoreCsv() {}

    /**
     * Il testo di un file scaricato: UTF-8 se lo è davvero, altrimenti
     * Windows-1252, che è come esportano i loro CSV molti comuni (dati.veneto.it
     * ne ha di tutti e due i tipi, anche nello stesso modello).
     */
    static String testo(byte[] contenuto) {
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(contenuto))
                    .toString();
        } catch (CharacterCodingException nonUtf8) {
            return new String(contenuto, WINDOWS_1252);
        }
    }

    /** Punto e virgola o virgola, quello che la prima riga usa di più. */
    static char separatore(String testo) {
        int fine = testo.indexOf('\n');
        String intestazione = fine < 0 ? testo : testo.substring(0, fine);
        long puntiEVirgola = intestazione.chars().filter(c -> c == ';').count();
        long virgole = intestazione.chars().filter(c -> c == ',').count();
        return puntiEVirgola > virgole ? ';' : ',';
    }

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

    /**
     * Le colonne di un CSV per nome, senza badare a maiuscole e spazi: ogni
     * campo si chiede con i nomi che ha nei dataset delle varie fonti, e vale
     * il primo che c'è.
     */
    static final class Colonne {

        private final Map<String, Integer> indici = new LinkedHashMap<>();

        Colonne(List<String> intestazione) {
            for (int i = 0; i < intestazione.size(); i++) {
                indici.putIfAbsent(nome(intestazione.get(i)), i);
            }
        }

        private static String nome(String colonna) {
            return colonna.strip().toLowerCase(Locale.ROOT);
        }

        boolean ha(String... nomi) {
            for (String nome : nomi) {
                if (indici.containsKey(nome(nome))) {
                    return true;
                }
            }
            return false;
        }

        /** Il valore, senza spazi doppi né a capo; vuoto se la colonna non c'è. */
        String valore(List<String> riga, String... nomi) {
            for (String nome : nomi) {
                Integer indice = indici.get(nome(nome));
                if (indice != null) {
                    return indice < riga.size() ? riga.get(indice).strip().replaceAll("\\s+", " ") : "";
                }
            }
            return "";
        }
    }
}
