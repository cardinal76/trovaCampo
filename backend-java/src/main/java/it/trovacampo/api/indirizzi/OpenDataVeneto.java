package it.trovacampo.api.indirizzi;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * I dataset comunali degli impianti sportivi pubblicati su dati.veneto.it
 * (tutti CC BY 4.0), in {@code trovacampo.indirizzi.open-data.veneto}.
 *
 * @param intervallo fra due download: il robots.txt del portale chiede
 *     {@code Crawl-delay: 10}, e sotto i dieci secondi non si scende
 * @param dataset uno per comune
 */
@ConfigurationProperties("trovacampo.indirizzi.open-data.veneto")
public record OpenDataVeneto(Duration intervallo, List<Dataset> dataset) {

    static final Duration MINIMO = Duration.ofSeconds(10);

    public OpenDataVeneto {
        intervallo = intervallo == null || intervallo.compareTo(MINIMO) < 0 ? MINIMO : intervallo;
        dataset = dataset == null ? List.of() : List.copyOf(dataset);
    }

    /**
     * @param comune il comune degli impianti, per i file che non lo scrivono
     *     in ogni riga (Verona)
     * @param titolo il titolo della scheda sul portale, per l'attribuzione
     * @param url il CSV del datastore CKAN del portale
     */
    public record Dataset(String comune, String titolo, String url) {

        /** «Comune di Verona - Impianti sportivi, dati.veneto.it (CC BY 4.0)». */
        String attribuzione() {
            return titolo + ", dati.veneto.it (" + FonteOpenData.VENETO.licenza() + ")";
        }
    }
}
