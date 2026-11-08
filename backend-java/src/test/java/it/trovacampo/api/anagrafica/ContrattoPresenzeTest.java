package it.trovacampo.api.anagrafica;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

/**
 * Il contratto con l'API pubblica di presenze: {@code contratto-presenze.json}
 * elenca i campi che il client {@link AnagraficaPresenze} legge. Il file e'
 * identico a {@code contratto-trovacampo.json} nel repo di presenze, dove un
 * test controlla che i DTO li emettano ancora tutti.
 *
 * <p>Qui si controlla l'altra meta': il client legge esattamente quei campi
 * (se ne serve uno nuovo, lo si aggiunge al file e si copia in presenze), e
 * ignora quelli che non conosce, cosi' un campo nuovo in presenze non lo rompe.
 */
class ContrattoPresenzeTest {

    private static final String PACCHETTO = AnagraficaPresenze.class.getName() + "$";

    private final Map<String, Set<String>> campiPerRecord = leggiContratto();

    @Test
    void ilClientLeggeEsattamenteICampiDelContratto() throws Exception {
        for (Map.Entry<String, Set<String>> voce : campiPerRecord.entrySet()) {
            Class<?> record = Class.forName(PACCHETTO + voce.getKey());
            assertThat(componenti(record))
                    .as("campi letti da AnagraficaPresenze.%s", voce.getKey())
                    .isEqualTo(voce.getValue());
        }
    }

    @Test
    void ognuniDeiRecordDelClientEnelContratto() {
        Set<String> record = new TreeSet<>();
        for (Class<?> annidata : AnagraficaPresenze.class.getDeclaredClasses()) {
            if (annidata.isRecord()) {
                record.add(annidata.getSimpleName());
            }
        }
        assertThat(campiPerRecord.keySet())
                .as("record del client e voci del contratto")
                .containsExactlyInAnyOrderElementsOf(record);
    }

    @Test
    void ilClientIgnoraICampiCheNonConosce() throws Exception {
        for (String nome : campiPerRecord.keySet()) {
            Class<?> record = Class.forName(PACCHETTO + nome);
            JsonIgnoreProperties ignora = record.getAnnotation(JsonIgnoreProperties.class);
            assertThat(ignora != null && ignora.ignoreUnknown())
                    .as("%s ignora i campi sconosciuti", nome)
                    .isTrue();
        }
    }

    private static Set<String> componenti(Class<?> record) {
        return new TreeSet<>(
                Arrays.stream(record.getRecordComponents()).map(c -> c.getName()).toList());
    }

    /** Record del client -> unione dei campi di tutte le voci che lo riguardano. */
    private static Map<String, Set<String>> leggiContratto() {
        try (InputStream in =
                ContrattoPresenzeTest.class.getResourceAsStream("/contratto-presenze.json")) {
            JsonNode radice = new ObjectMapper().readTree(in);
            Map<String, Set<String>> risultato = new LinkedHashMap<>();
            for (JsonNode voce : radice.get("voci")) {
                List<String> campi = new ArrayList<>();
                voce.get("campi").forEach(c -> campi.add(c.asText()));
                risultato
                        .computeIfAbsent(voce.get("client").asText(), k -> new TreeSet<>())
                        .addAll(campi);
            }
            return risultato;
        } catch (Exception e) {
            throw new IllegalStateException("contratto-presenze.json illeggibile", e);
        }
    }
}
