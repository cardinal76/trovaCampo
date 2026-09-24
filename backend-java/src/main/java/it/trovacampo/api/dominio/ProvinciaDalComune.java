package it.trovacampo.api.dominio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * La sigla della provincia ricavata dal comune del campo.
 *
 * <p>Presenze e i Comunicati Ufficiali danno il comune ma non la provincia,
 * e senza provincia l'elenco e la mappa non si possono filtrare. I campi
 * sono tutti del Lazio, quindi basta l'elenco ISTAT dei suoi 378 comuni
 * ({@code comuni-lazio.csv}): è un dato che non cambia e non chiede
 * servizi esterni.
 *
 * <p>Il comune a volte arriva con la frazione ("ROMA (TUSCOLANO)",
 * "Ostia (Roma)", "MONTEROTONDO SCALO") o accorciato ("CISTERNA"): si prova
 * prima il nome intero, poi le parti prima e dentro la parentesi, poi il
 * comune più lungo con cui il nome comincia, infine l'unico comune che
 * comincia col nome. Quello che non si riconosce resta senza provincia:
 * meglio "sconosciuta" che una provincia sbagliata.
 */
public final class ProvinciaDalComune {

    private static final Map<String, String> SIGLE = carica();

    private ProvinciaDalComune() {}

    /** La sigla ("RM", "LT"...), o "" se il comune non è uno del Lazio riconoscibile. */
    public static String sigla(String comune) {
        String nome = chiave(comune);
        if (nome.isEmpty()) {
            return "";
        }
        int parentesi = Objects.requireNonNullElse(comune, "").indexOf('(');
        List<String> tentativi =
                parentesi < 0
                        ? List.of(nome)
                        : List.of(
                                nome,
                                chiave(comune.substring(0, parentesi)),
                                chiave(comune.substring(parentesi + 1)));
        for (String tentativo : tentativi) {
            String sigla = SIGLE.get(tentativo);
            if (sigla != null) {
                return sigla;
            }
        }
        return perInizio(tentativi.get(tentativi.size() > 1 ? 1 : 0));
    }

    /** "MONTEROTONDO SCALO" è a Monterotondo; "CISTERNA" è Cisterna di Latina. */
    private static String perInizio(String nome) {
        if (nome.isEmpty()) {
            return "";
        }
        String piuLungo = null;
        String unicoCheComincia = null;
        int cheCominciano = 0;
        for (String comune : SIGLE.keySet()) {
            if (nome.startsWith(comune + " ")
                    && (piuLungo == null || comune.length() > piuLungo.length())) {
                piuLungo = comune;
            }
            if (comune.startsWith(nome + " ")) {
                unicoCheComincia = comune;
                cheCominciano++;
            }
        }
        if (piuLungo != null) {
            return SIGLE.get(piuLungo);
        }
        return cheCominciano == 1 ? SIGLE.get(unicoCheComincia) : "";
    }

    /** Minuscolo, senza accenti e con la punteggiatura ridotta a spazi: "Rocca d'Arce" è "rocca d arce". */
    private static String chiave(String testo) {
        return Testo.normalizza(testo).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private static Map<String, String> carica() {
        Map<String, String> sigle = new HashMap<>();
        try (InputStream file = ProvinciaDalComune.class.getResourceAsStream("/comuni-lazio.csv");
                BufferedReader righe =
                        new BufferedReader(
                                new InputStreamReader(
                                        Objects.requireNonNull(file, "manca comuni-lazio.csv"),
                                        StandardCharsets.UTF_8))) {
            String riga;
            while ((riga = righe.readLine()) != null) {
                if (riga.isBlank() || riga.startsWith("#")) {
                    continue;
                }
                String[] parti = riga.split(";");
                sigle.put(chiave(parti[0]), parti[1].strip());
            }
        } catch (IOException eccezione) {
            throw new UncheckedIOException(eccezione);
        }
        return Map.copyOf(sigle);
    }
}
