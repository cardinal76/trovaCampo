package it.trovacampo.api.prospect;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.trovacampo.api.indirizzi.Confidenza;
import it.trovacampo.api.indirizzi.FonteCampiLnd;
import it.trovacampo.api.indirizzi.FonteIndirizzo;
import it.trovacampo.api.indirizzi.IndirizzoMigliore;
import it.trovacampo.api.indirizzi.LocalizzatoreSocieta;
import it.trovacampo.api.indirizzi.SocietaDaLocalizzare;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Dà l'indirizzo del campo di gioco ai prospect di calcio del Lazio con le
 * fonti di {@link LocalizzatoreSocieta}, senza chiamare Nominatim: prima i
 * campi LND (schede di TrovaCampo, poi anagrafica di presenze, con le regole
 * di {@link FonteCampiLnd}), poi il censimento degli impianti in open data.
 * La ricerca su OpenStreetMap, che va a una richiesta al secondo, la fa
 * {@link ArricchimentoProspect}, in sottofondo.
 *
 * <p>Una fonte migliore prende il posto di una peggiore, mai il contrario:
 * un prospect già trovato su OSM che ora ha il campo LND passa al campo LND,
 * uno con il campo LND non torna all'open data.
 */
@Service
public class CollegamentoCampi {

    private static final Logger log = LoggerFactory.getLogger(CollegamentoCampi.class);

    static final String LAZIO = "Lazio";

    private final ProspectRepository prospect;
    private final LocalizzatoreSocieta localizzatore;
    private final FonteCampiLnd campiLnd;

    public CollegamentoCampi(
            ProspectRepository prospect, LocalizzatoreSocieta localizzatore, FonteCampiLnd campiLnd) {
        this.prospect = prospect;
        this.localizzatore = localizzatore;
        this.campiLnd = campiLnd;
    }

    public record Esito(
            int prospectCalcioLazio,
            int collegati,
            int conCoordinate,
            Map<FonteIndirizzo, Integer> perFonte,
            Map<String, Integer> perRegola,
            Map<Confidenza, Integer> perConfidenza,
            boolean presenzeRaggiungibile) {

        /** La percentuale di società di calcio del Lazio con l'indirizzo del campo. */
        @JsonProperty
        public double copertura() {
            return prospectCalcioLazio == 0 ? 0 : Math.round(1000.0 * collegati / prospectCalcioLazio) / 10.0;
        }
    }

    public Esito collega() {
        campiLnd.ricarica();
        List<Prospect> daCollegare = prospect.findByRegioneAndCalcioTrue(LAZIO);
        List<Prospect> cambiati = new ArrayList<>();
        Map<String, Integer> perRegola = new LinkedHashMap<>();
        Instant adesso = Instant.now();
        for (Prospect p : daCollegare) {
            Optional<IndirizzoMigliore> trovato =
                    localizzatore.trova(daLocalizzare(p), LocalizzatoreSocieta.Opzioni.SENZA_RETE);
            if (trovato.isPresent() && applica(p, trovato.get(), adesso)) {
                cambiati.add(p);
                perRegola.merge(trovato.get().regola(), 1, Integer::sum);
            }
        }
        if (!cambiati.isEmpty()) {
            prospect.saveAll(cambiati);
        }
        Esito esito = conta(daCollegare, perRegola, campiLnd.presenzeRaggiungibile());
        log.info(
                "Collegamento dei prospect del Lazio: {} su {} con il campo ({}%), {} con le coordinate, per fonte {}",
                esito.collegati(), esito.prospectCalcioLazio(), esito.copertura(), esito.conCoordinate(),
                esito.perFonte());
        return esito;
    }

    /** Quanti prospect di calcio del Lazio hanno l'indirizzo, adesso, senza cercare niente. */
    public Esito copertura() {
        return conta(prospect.findByRegioneAndCalcioTrue(LAZIO), Map.of(), true);
    }

    private static Esito conta(List<Prospect> tutti, Map<String, Integer> perRegola, boolean presenze) {
        Map<FonteIndirizzo, Integer> perFonte = new EnumMap<>(FonteIndirizzo.class);
        Map<Confidenza, Integer> perConfidenza = new EnumMap<>(Confidenza.class);
        int collegati = 0;
        int conCoordinate = 0;
        for (Prospect p : tutti) {
            if (p.getFonteIndirizzo() != null) {
                collegati++;
                perFonte.merge(p.getFonteIndirizzo(), 1, Integer::sum);
                if (p.getConfidenzaIndirizzo() != null) {
                    perConfidenza.merge(p.getConfidenzaIndirizzo(), 1, Integer::sum);
                }
            }
            if (p.getLat() != null && p.getLng() != null) {
                conCoordinate++;
            }
        }
        return new Esito(tutti.size(), collegati, conCoordinate, perFonte, perRegola, perConfidenza, presenze);
    }

    static SocietaDaLocalizzare daLocalizzare(Prospect p) {
        return new SocietaDaLocalizzare(p.getDenominazione(), p.getComune(), p.getCodiceFiscale());
    }

    /**
     * Scrive l'indirizzo nel prospect se la fonte è almeno buona quanto
     * quella che ha già. Lo stesso indirizzo ritrovato senza coordinate (la
     * ricerca senza rete) tiene quelle della geocodifica di prima.
     *
     * @return se il prospect è cambiato
     */
    static boolean applica(Prospect p, IndirizzoMigliore trovato, Instant adesso) {
        FonteIndirizzo attuale = p.getFonteIndirizzo();
        if (attuale != null && trovato.fonte().compareTo(attuale) > 0) {
            return false;
        }
        Double lat = trovato.lat();
        Double lng = trovato.lng();
        if (!trovato.haCoordinate() && trovato.fonte() == attuale
                && Objects.equals(trovato.indirizzo(), p.getIndirizzo())) {
            lat = p.getLat();
            lng = p.getLng();
        }
        p.setNomeImpianto(trovato.impianto())
                .setIndirizzo(trovato.indirizzo())
                .setComuneCampo(trovato.comune())
                .setLat(lat)
                .setLng(lng)
                .setFonteIndirizzo(trovato.fonte())
                .setConfidenzaIndirizzo(trovato.confidenza())
                .setCollegamento(trovato.regola())
                .setSocietaTrovaCampoId(trovato.societaTrovaCampoId())
                .setAnagraficaImpiantoId(trovato.anagraficaImpiantoId())
                .setAggiornato(adesso);
        return true;
    }
}
