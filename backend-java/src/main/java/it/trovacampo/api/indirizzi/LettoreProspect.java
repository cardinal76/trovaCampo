package it.trovacampo.api.indirizzi;

import it.trovacampo.api.dominio.ProvinciaDalComune;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Le società di calcio del Lazio nella collection {@code prospect}, quella
 * del Registro RASD.
 *
 * <p>La collection la riempie un altro lavoro, e la forma dei suoi documenti
 * qui non si dà per scontata: per ogni dato si prova una lista di nomi di
 * campo (anche annidati, "sede.comune"), il primo che c'è vale. Si legge solo
 * quello che serve all'abbinamento -- codice fiscale, denominazione, comune,
 * matricola FIGC -- e niente dei legali rappresentanti.
 *
 * <p>Lazio: regione "Lazio", o provincia RM, LT, FR, VT, RI; se i documenti
 * non hanno né l'una né l'altra, il comune della sede fra i 378 del Lazio.
 * Calcio: fra gli organismi affilianti (o discipline) c'è FIGC o "calcio";
 * se i documenti non hanno nessuno di quei campi il filtro non si applica,
 * e il report lo dice.
 */
@Component
public class LettoreProspect {

    static final List<String> CODICE_FISCALE = List.of("codiceFiscale", "cf", "_id");
    static final List<String> DENOMINAZIONE = List.of("denominazione", "nome", "ragioneSociale");
    static final List<String> COMUNE = List.of("comune", "comuneSede", "sede.comune", "indirizzo.comune");
    static final List<String> CODICE_FIGC = List.of("codiceFigc", "matricolaFigc", "matricola");
    static final List<String> REGIONE = List.of("regione", "regioneSede", "sede.regione");
    static final List<String> PROVINCIA = List.of("provincia", "siglaProvincia", "sede.provincia");
    static final List<String> CALCIO =
            List.of("organismiAffilianti", "organismi", "affiliazioni", "federazioni", "discipline", "sport");

    private static final Set<String> PROVINCE_LAZIO = Set.of("RM", "LT", "FR", "VT", "RI");
    private static final Pattern LAZIO = Pattern.compile("lazio", Pattern.CASE_INSENSITIVE);

    private final MongoTemplate mongo;
    private final String collection;

    public LettoreProspect(
            MongoTemplate mongo, @Value("${trovacampo.indirizzi.prospect.collection:prospect}") String collection) {
        this.mongo = mongo;
        this.collection = collection;
    }

    /** Una società del registro, solo i dati che servono a trovarne il campo. */
    public record SocietaProspect(String codiceFiscale, String denominazione, String comune, String codiceFigc) {
        SocietaDaLocalizzare daLocalizzare() {
            return new SocietaDaLocalizzare(denominazione, comune, codiceFiscale, codiceFigc);
        }
    }

    /**
     * @param societa le società di calcio del Lazio
     * @param filtroCalcio se il filtro sul calcio si è potuto applicare
     */
    public record Lettura(List<SocietaProspect> societa, boolean filtroCalcio, String criterioLazio) {}

    public String collection() {
        return collection;
    }

    public boolean esiste() {
        return mongo.collectionExists(collection);
    }

    public Lettura societaDiCalcioDelLazio() {
        Document esempio = mongo.findOne(new Query(), Document.class, collection);
        if (esempio == null) {
            return new Lettura(List.of(), false, "collection vuota");
        }
        Optional<String> regione = primoPresente(esempio, REGIONE);
        Optional<String> provincia = primoPresente(esempio, PROVINCIA);
        boolean filtroCalcio = primoPresente(esempio, CALCIO).isPresent();

        Query query = new Query();
        String criterio;
        if (regione.isPresent()) {
            query.addCriteria(Criteria.where(regione.get()).regex(LAZIO));
            criterio = regione.get() + " = Lazio";
        } else if (provincia.isPresent()) {
            query.addCriteria(Criteria.where(provincia.get()).in(PROVINCE_LAZIO));
            criterio = provincia.get() + " in " + PROVINCE_LAZIO;
        } else {
            criterio = "comune della sede fra i comuni del Lazio";
        }

        List<SocietaProspect> societa = new ArrayList<>();
        try (Stream<Document> documenti = mongo.stream(query, Document.class, collection)) {
            documenti.forEach(
                    documento -> {
                        SocietaProspect letta = leggi(documento);
                        if (letta == null) {
                            return;
                        }
                        if (regione.isEmpty() && provincia.isEmpty()
                                && ProvinciaDalComune.sigla(letta.comune()).isEmpty()) {
                            return;
                        }
                        if (filtroCalcio && !diCalcio(documento)) {
                            return;
                        }
                        societa.add(letta);
                    });
        }
        return new Lettura(societa, filtroCalcio, criterio);
    }

    static SocietaProspect leggi(Document documento) {
        String codiceFiscale = testo(documento, CODICE_FISCALE);
        String denominazione = testo(documento, DENOMINAZIONE);
        if (codiceFiscale == null || denominazione == null) {
            return null;
        }
        return new SocietaProspect(
                codiceFiscale, denominazione, testo(documento, COMUNE), testo(documento, CODICE_FIGC));
    }

    /** FIGC o "calcio" in uno dei campi degli organismi o delle discipline, in qualsiasi forma. */
    static boolean diCalcio(Document documento) {
        for (String campo : CALCIO) {
            Object valore = valore(documento, campo);
            if (valore != null) {
                String testo = piatto(valore).toUpperCase(Locale.ITALIAN);
                if (testo.contains("FIGC") || testo.contains("CALCIO") || testo.contains("GIUOCO")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String piatto(Object valore) {
        if (valore instanceof Collection<?> elenco) {
            return String.join(" ", elenco.stream().map(LettoreProspect::piatto).toList());
        }
        if (valore instanceof Map<?, ?> mappa) {
            return String.join(" ", mappa.values().stream().map(LettoreProspect::piatto).toList());
        }
        return String.valueOf(valore);
    }

    private static Optional<String> primoPresente(Document documento, List<String> campi) {
        return campi.stream().filter(campo -> valore(documento, campo) != null).findFirst();
    }

    private static String testo(Document documento, List<String> campi) {
        for (String campo : campi) {
            Object valore = valore(documento, campo);
            if (valore != null && !String.valueOf(valore).isBlank()) {
                return String.valueOf(valore).strip();
            }
        }
        return null;
    }

    /** Il valore di un campo, anche annidato ("sede.comune"). */
    static Object valore(Document documento, String percorso) {
        Object corrente = documento;
        for (String parte : percorso.split("\\.")) {
            if (!(corrente instanceof Map<?, ?> mappa)) {
                return null;
            }
            corrente = mappa.get(parte);
        }
        return corrente;
    }
}
