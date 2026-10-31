package it.trovacampo.api.indirizzi;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * I censimenti open data degli impianti sportivi che l'amministrazione può
 * importare in {@code impianto_open_data}, uno alla volta e a mano
 * ({@code POST /api/admin/indirizzi/impianti-open-data?fonte=...}). Fonti,
 * licenze e richieste fatte: tools/prospect/INDIRIZZI_LAZIO.md.
 *
 * @param parametro come si chiede dall'endpoint ({@code ?fonte=cmrc})
 * @param sigla il valore di {@link ImpiantoOpenData#fonte()}: non si cambia,
 *     è anche nell'id dei documenti già salvati
 */
public enum FonteOpenData {
    CMRC(
            "cmrc",
            "cmrc-censimento",
            "Censimento impianti sportivi di Roma e Provincia, Città metropolitana di Roma Capitale",
            "CC0 1.0"),
    VENETO(
            "veneto",
            "veneto-comuni",
            "Impianti sportivi dei comuni del Veneto, dati.veneto.it",
            "CC BY 4.0");

    private final String parametro;
    private final String sigla;
    private final String nome;
    private final String licenza;

    FonteOpenData(String parametro, String sigla, String nome, String licenza) {
        this.parametro = parametro;
        this.sigla = sigla;
        this.nome = nome;
        this.licenza = licenza;
    }

    public String parametro() {
        return parametro;
    }

    public String sigla() {
        return sigla;
    }

    public String nome() {
        return nome;
    }

    public String licenza() {
        return licenza;
    }

    /** La riga di attribuzione di un impianto di questa fonte, quando il dataset non ne dà una sua. */
    public String attribuzione() {
        return nome + " (" + licenza + ")";
    }

    public static Optional<FonteOpenData> daParametro(String parametro) {
        if (parametro == null) {
            return Optional.empty();
        }
        String cercato = parametro.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(fonte -> fonte.parametro.equals(cercato)).findFirst();
    }

    public static Optional<FonteOpenData> daSigla(String sigla) {
        return Arrays.stream(values()).filter(fonte -> fonte.sigla.equals(sigla)).findFirst();
    }
}
