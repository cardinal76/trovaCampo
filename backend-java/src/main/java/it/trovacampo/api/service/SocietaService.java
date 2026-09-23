package it.trovacampo.api.service;

import it.trovacampo.api.dominio.Campionato;
import it.trovacampo.api.dominio.Societa;
import it.trovacampo.api.dominio.Testo;
import it.trovacampo.api.dominio.TipoCampionato;
import it.trovacampo.api.repository.SocietaRepository;
import it.trovacampo.api.web.DatiNonValidiException;
import it.trovacampo.api.web.ModificaSocietaRequest;
import it.trovacampo.api.web.NuovoCampoRequest;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class SocietaService {

    private final SocietaRepository repository;
    private final Geocoding geocoding;

    public SocietaService(SocietaRepository repository, Geocoding geocoding) {
        this.repository = repository;
        this.geocoding = geocoding;
    }

    /** Funzione 1: ricerca per nome società, nome impianto, indirizzo o località. */
    public List<Societa> cerca(String nome) {
        String termine = Testo.normalizza(nome);

        if (termine.isEmpty()) {
            return List.of();
        }

        return repository.cercaPerTesto(Testo.perRegex(termine));
    }

    /** Tutti i campi, in ordine di nome della società, senza anagrafica. */
    public List<Societa> tuttiICampi() {
        return repository.tuttiICampi();
    }

    /** Funzioni 2 e 3: anagrafica e campionati della società. */
    public Optional<Societa> perId(String id) {
        return repository.findById(id);
    }

    /**
     * Inserisce un campo segnalato dagli utenti. Se la geocodifica non
     * riconosce l'indirizzo il campo viene salvato lo stesso, senza
     * coordinate: comparirà in ricerca ma non come pin sulla mappa.
     */
    public Societa inserisci(NuovoCampoRequest richiesta) {
        Societa societa =
                new Societa()
                        .setSiglaSocieta("")
                        .setNomeSocieta(richiesta.nomeSocieta().trim())
                        .setComitatoRegionale("")
                        .setNomeImpianto(richiesta.nomeImpianto().trim())
                        .setIndirizzoImpianto(richiesta.indirizzoImpianto().trim())
                        .setLocalitaImpianto("")
                        .setProvinciaImpianto("");

        geocoding
                .geocodifica(societa.getIndirizzoImpianto())
                .ifPresent(
                        coordinate -> societa.setLat(coordinate.lat()).setLng(coordinate.lng()));

        return repository.save(aggiornaTestoRicerca(societa));
    }

    /**
     * Sostituisce la scheda con quella mandata da chi amministra. Vuoto se
     * la società non esiste.
     *
     * <p>Le coordinate seguono tre regole: se il modulo ne porta di diverse
     * dalle attuali, valgono quelle (correzione a mano del segnaposto); se le
     * svuota, o se cambia l'indirizzo lasciandole com'erano, si tolgono e le
     * ricalcola la geocodifica automatica; altrimenti restano.
     */
    public Optional<Societa> modifica(String id, ModificaSocietaRequest richiesta) {
        if ((richiesta.lat() == null) != (richiesta.lng() == null)) {
            throw new DatiNonValidiException("latitudine e longitudine vanno date insieme");
        }

        return repository
                .findById(id)
                .map(
                        societa -> {
                            String indirizzo = richiesta.indirizzoImpianto().strip();
                            String localita = oVuoto(richiesta.localitaImpianto());
                            String provincia = oVuoto(richiesta.provinciaImpianto()).toUpperCase(Locale.ITALIAN);
                            boolean spostata =
                                    !indirizzo.equals(societa.getIndirizzoImpianto())
                                            || !localita.equals(Objects.toString(societa.getLocalitaImpianto(), ""))
                                            || !provincia.equals(Objects.toString(societa.getProvinciaImpianto(), ""));
                            boolean coordinateCorrette =
                                    richiesta.lat() != null
                                            && (!richiesta.lat().equals(societa.getLat())
                                                    || !richiesta.lng().equals(societa.getLng()));

                            if (coordinateCorrette) {
                                societa.setLat(richiesta.lat()).setLng(richiesta.lng());
                            } else if (richiesta.lat() == null || spostata) {
                                societa.setLat(null).setLng(null);
                            }
                            if (coordinateCorrette || societa.getLat() == null) {
                                // Un indirizzo nuovo, o un segnaposto messo a mano,
                                // chiudono la partita con i tentativi andati male.
                                societa.setGeocodificaFallitaVersione(null);
                            }

                            boolean scuolaCalcio = Boolean.TRUE.equals(richiesta.scuolaCalcio());
                            societa.setSiglaSocieta(oVuoto(richiesta.siglaSocieta()))
                                    .setNomeSocieta(richiesta.nomeSocieta().strip())
                                    .setComitatoRegionale(oVuoto(richiesta.comitatoRegionale()))
                                    .setNomeImpianto(richiesta.nomeImpianto().strip())
                                    .setIndirizzoImpianto(indirizzo)
                                    .setLocalitaImpianto(localita)
                                    .setProvinciaImpianto(provincia)
                                    .setMatricola(oNull(richiesta.matricola()))
                                    .setPresidente(oNull(richiesta.presidente()))
                                    .setIndirizzoSede(oNull(richiesta.indirizzoSede()))
                                    .setTelefono(oNull(richiesta.telefono()))
                                    .setFax(oNull(richiesta.fax()))
                                    .setEmail(oNull(richiesta.email()))
                                    .setSitoWeb(oNull(richiesta.sitoWeb()))
                                    .setScuolaCalcio(richiesta.scuolaCalcio())
                                    .setPrezziScuolaCalcio(
                                            scuolaCalcio ? oNull(richiesta.prezziScuolaCalcio()) : null)
                                    .setCampionati(campionati(richiesta.campionati()));

                            return repository.save(aggiornaTestoRicerca(societa));
                        });
    }

    /** Le righe senza descrizione sono righe lasciate vuote nel modulo. */
    private static List<Campionato> campionati(List<Campionato> dalModulo) {
        if (dalModulo == null) {
            return null;
        }
        List<Campionato> puliti =
                dalModulo.stream()
                        .filter(c -> c != null && c.descrizione() != null && !c.descrizione().isBlank())
                        .map(
                                c ->
                                        new Campionato(
                                                c.descrizione().strip(),
                                                oVuoto(c.girone()),
                                                oVuoto(c.comitato()),
                                                c.tipo() == null ? TipoCampionato.AGONISTICA : c.tipo()))
                        .toList();
        return puliti.isEmpty() ? null : puliti;
    }

    /** I campi facoltativi dell'anagrafica: vuoto vuol dire assente, e sparisce dal JSON. */
    private static String oNull(String testo) {
        return testo == null || testo.isBlank() ? null : testo.strip();
    }

    /** Sigla, comitato, località e provincia restano "" come nei campi inseriti dall'app. */
    private static String oVuoto(String testo) {
        return testo == null ? "" : testo.strip();
    }

    /** Ricalcola la copia normalizzata dei campi su cui lavora la ricerca. */
    public static Societa aggiornaTestoRicerca(Societa societa) {
        String testo =
                Stream.of(
                                societa.getNomeSocieta(),
                                societa.getNomeImpianto(),
                                societa.getIndirizzoImpianto(),
                                societa.getLocalitaImpianto())
                        .map(Testo::normalizza)
                        .filter(parte -> !parte.isEmpty())
                        .reduce((a, b) -> a + " | " + b)
                        .orElse("");

        return societa.setTestoRicerca(testo);
    }
}
