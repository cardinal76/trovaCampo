package it.trovacampo.api.indirizzi;

import it.trovacampo.api.dominio.ProvinciaDalComune;
import it.trovacampo.api.dominio.Societa;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Fonte 1: i campi di gioco dei Comunicati Ufficiali LND che TrovaCampo ha
 * già, nella collection {@code societa} (sincronizzati dall'anagrafica di
 * presenze o importati dai file del Comitato). Ogni riga è una coppia
 * società–campo dove gioca in casa.
 *
 * <p>Gli abbinamenti, dal più sicuro:
 *
 * <ol>
 *   <li>la matricola FIGC, se la società la porta e la riga pure: ALTA;
 *   <li>il nome, sopra la {@linkplain NomiSocieta#SOGLIA soglia}, fra le
 *       società che giocano nel comune della sede: ALTA da 0,9, MEDIA sotto;
 *   <li>il nome praticamente identico (da 0,95) a quello di una sola società
 *       LND in tutta la regione, con il campo in un altro comune della stessa
 *       provincia: MEDIA. È l'unica eccezione alla regola dello stesso comune,
 *       ed è quella che serve davvero: "A.S.D. ARNARA" ha sede ad Arnara e
 *       gioca a Frosinone, "CASSINO CALCIO 1924" gioca a Casalattico.
 * </ol>
 */
@Component
public class FonteCampiLnd {

    /** Le righe si rileggono da Mongo al massimo ogni dieci minuti: un arricchimento ne fa migliaia di ricerche. */
    private static final Duration VALIDITA = Duration.ofMinutes(10);

    static final double SOGLIA_ALTA = 0.9;
    static final double SOGLIA_ALTRO_COMUNE = 0.95;

    private final MongoTemplate mongo;
    private final Clock orologio;
    private List<CampoLnd> campi;
    private Instant lettiIl;

    @Autowired
    public FonteCampiLnd(MongoTemplate mongo) {
        this(mongo, Clock.systemUTC());
    }

    FonteCampiLnd(MongoTemplate mongo, Clock orologio) {
        this.mongo = mongo;
        this.orologio = orologio;
    }

    /** Una riga di {@code societa}: solo quello che serve all'abbinamento. */
    record CampoLnd(
            String nomeSocieta,
            String nomeImpianto,
            String indirizzo,
            String comune,
            String provincia,
            Double lat,
            Double lng,
            String matricola,
            List<String> parole,
            List<String> anni) {

        /** Le parole del nome si estraggono una volta sola, non a ogni ricerca. */
        CampoLnd(
                String nomeSocieta,
                String nomeImpianto,
                String indirizzo,
                String comune,
                String provincia,
                Double lat,
                Double lng,
                String matricola) {
            this(nomeSocieta, nomeImpianto, indirizzo, comune, provincia, lat, lng, matricola,
                    NomiSocieta.parole(nomeSocieta), NomiSocieta.anni(nomeSocieta));
        }

        static CampoLnd di(Societa societa) {
            return new CampoLnd(
                    societa.getNomeSocieta(),
                    societa.getNomeImpianto(),
                    societa.getIndirizzoImpianto(),
                    societa.getLocalitaImpianto(),
                    societa.getProvinciaImpianto(),
                    societa.getLat(),
                    societa.getLng(),
                    societa.getMatricola());
        }

        boolean haIndirizzo() {
            return indirizzo != null && !indirizzo.isBlank();
        }
    }

    public Optional<IndirizzoMigliore> cerca(SocietaDaLocalizzare societa) {
        return cerca(societa, campi());
    }

    /** Quante righe ha la fonte: per il report. */
    public int righe() {
        return campi().size();
    }

    synchronized List<CampoLnd> campi() {
        Instant adesso = orologio.instant();
        if (campi == null || lettiIl.plus(VALIDITA).isBefore(adesso)) {
            Query query = new Query();
            query.fields()
                    .include(
                            "nomeSocieta", "nomeImpianto", "indirizzoImpianto", "localitaImpianto",
                            "provinciaImpianto", "lat", "lng", "matricola");
            campi =
                    mongo.find(query, Societa.class).stream()
                            .map(CampoLnd::di)
                            .filter(CampoLnd::haIndirizzo)
                            .toList();
            lettiIl = adesso;
        }
        return campi;
    }

    static Optional<IndirizzoMigliore> cerca(SocietaDaLocalizzare societa, List<CampoLnd> campi) {
        Optional<IndirizzoMigliore> perMatricola = perMatricola(societa, campi);
        if (perMatricola.isPresent()) {
            return perMatricola;
        }

        List<String> parole = NomiSocieta.parole(societa.denominazione());
        List<String> anni = NomiSocieta.anni(societa.denominazione());
        List<Punteggio> punteggi =
                campi.stream()
                        .map(
                                campo ->
                                        new Punteggio(
                                                campo,
                                                NomiSocieta.somiglianza(
                                                        parole, anni, campo.parole(), campo.anni())))
                        .filter(punteggio -> punteggio.valore() >= NomiSocieta.SOGLIA)
                        .toList();

        Optional<Punteggio> stessoComune =
                punteggi.stream()
                        .filter(punteggio -> Comuni.stesso(punteggio.campo().comune(), societa.comune()))
                        .max(MIGLIORE);
        if (stessoComune.isPresent()) {
            Punteggio migliore = stessoComune.get();
            return Optional.of(
                    indirizzo(
                            migliore.campo(),
                            migliore.valore() >= SOGLIA_ALTA ? Confidenza.ALTA : Confidenza.MEDIA,
                            migliore.valore()));
        }
        return inAltroComune(societa, punteggi);
    }

    /**
     * Il nome identico a una sola società LND della regione, con il campo nella
     * stessa provincia. Si contano i nomi come li scrive LND: "SS ROMULEA" a
     * Roma e "ROMULEA" a Rieti sono due società, e da Frascati non si sceglie.
     */
    private static Optional<IndirizzoMigliore> inAltroComune(
            SocietaDaLocalizzare societa, List<Punteggio> punteggi) {
        List<Punteggio> identici =
                punteggi.stream().filter(punteggio -> punteggio.valore() >= SOGLIA_ALTRO_COMUNE).toList();
        long societaDiverse =
                identici.stream()
                        .map(punteggio -> punteggio.campo().nomeSocieta().strip().toUpperCase())
                        .distinct()
                        .count();
        if (societaDiverse != 1) {
            return Optional.empty();
        }
        String provinciaSede = ProvinciaDalComune.sigla(societa.comune());
        return identici.stream()
                .filter(
                        punteggio ->
                                !provinciaSede.isEmpty()
                                        && provinciaSede.equals(provinciaDelCampo(punteggio.campo())))
                .max(MIGLIORE)
                .map(punteggio -> indirizzo(punteggio.campo(), Confidenza.MEDIA, punteggio.valore()));
    }

    private static String provinciaDelCampo(CampoLnd campo) {
        if (campo.provincia() != null && !campo.provincia().isBlank()) {
            return campo.provincia().strip().toUpperCase();
        }
        return ProvinciaDalComune.sigla(campo.comune());
    }

    private static Optional<IndirizzoMigliore> perMatricola(
            SocietaDaLocalizzare societa, List<CampoLnd> campi) {
        String matricola = matricola(societa.codiceFigc());
        if (matricola.isEmpty()) {
            return Optional.empty();
        }
        return campi.stream()
                .filter(campo -> matricola.equals(matricola(campo.matricola())))
                .max(Comparator.comparing((CampoLnd campo) -> campo.lat() != null))
                .map(campo -> indirizzo(campo, Confidenza.ALTA, 1));
    }

    /** "0945580" e "945580" sono la stessa matricola. */
    private static String matricola(String valore) {
        if (valore == null) {
            return "";
        }
        return valore.strip().replaceFirst("^0+", "");
    }

    private static IndirizzoMigliore indirizzo(CampoLnd campo, Confidenza confidenza, double punteggio) {
        return new IndirizzoMigliore(
                campo.indirizzo().strip(),
                campo.comune(),
                campo.lat(),
                campo.lng(),
                Fonte.CAMPI_LND,
                confidenza,
                punteggio,
                campo.nomeSocieta(),
                campo.nomeImpianto(),
                Fonte.CAMPI_LND.attribuzione());
    }

    private record Punteggio(CampoLnd campo, double valore) {}

    /** Il nome più simile; a parità, il campo che ha già la posizione. */
    private static final Comparator<Punteggio> MIGLIORE =
            Comparator.comparingDouble(Punteggio::valore)
                    .thenComparing(punteggio -> punteggio.campo().lat() != null);
}
