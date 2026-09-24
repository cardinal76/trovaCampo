package it.trovacampo.api.notifiche;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Component;

/**
 * Da dove vengono le chiavi VAPID.
 *
 * <p>Se ci sono {@code VAPID_PUBBLICA} e {@code VAPID_PRIVATA} valgono
 * quelle. Altrimenti, ed è il caso normale, il backend se le crea la prima
 * volta che servono e le salva in Mongo (collezione {@code configurazione},
 * documento {@code vapid}): nessun segreto da generare, copiare o incollare
 * da nessuna parte, e il backup di Mongo le comprende. Vanno conservate:
 * cambiarle vuol dire che tutti i browser già iscritti smettono di ricevere
 * notifiche finché non riaprono il sito.
 *
 * <p>Si leggono la prima volta che servono e non all'avvio, così un backend
 * senza Mongo (i test di contesto) parte lo stesso.
 */
@Component
public class DepositoChiaviVapid {

    private static final Logger log = LoggerFactory.getLogger(DepositoChiaviVapid.class);

    static final String COLLEZIONE = "configurazione";
    static final String DOCUMENTO = "vapid";

    @Document(collection = COLLEZIONE)
    record ChiaviSalvate(@Id String id, String pubblica, String privata) {}

    private final MongoTemplate mongo;
    private final String pubblicaDaAmbiente;
    private final String privataDaAmbiente;
    private volatile ChiaviVapid chiavi;

    public DepositoChiaviVapid(
            MongoTemplate mongo,
            @Value("${trovacampo.notifiche.vapid.pubblica:}") String pubblicaDaAmbiente,
            @Value("${trovacampo.notifiche.vapid.privata:}") String privataDaAmbiente) {
        this.mongo = mongo;
        this.pubblicaDaAmbiente = pubblicaDaAmbiente;
        this.privataDaAmbiente = privataDaAmbiente;
    }

    public ChiaviVapid chiavi() {
        ChiaviVapid lette = chiavi;
        if (lette == null) {
            synchronized (this) {
                if (chiavi == null) {
                    chiavi = carica();
                }
                lette = chiavi;
            }
        }
        return lette;
    }

    private ChiaviVapid carica() {
        if (!pubblicaDaAmbiente.isBlank() && !privataDaAmbiente.isBlank()) {
            log.info("Chiavi VAPID dalla configurazione");
            return ChiaviVapid.da(pubblicaDaAmbiente, privataDaAmbiente);
        }
        ChiaviSalvate salvate = mongo.findById(DOCUMENTO, ChiaviSalvate.class);
        if (salvate != null) {
            return ChiaviVapid.da(salvate.pubblica(), salvate.privata());
        }
        ChiaviVapid nuove = ChiaviVapid.nuove();
        try {
            // insert e non save: se due richieste le creano insieme, vince la
            // prima e l'altra rilegge quella, invece di sovrascriverla.
            mongo.insert(new ChiaviSalvate(DOCUMENTO, nuove.pubblicaBase64(), nuove.privataBase64()));
            // Solo la pubblica nel log: è quella che ogni browser già conosce.
            log.info("Chiavi VAPID create e salvate in Mongo; pubblica {}", nuove.pubblicaBase64());
            return nuove;
        } catch (DuplicateKeyException giaCreate) {
            ChiaviSalvate altre = mongo.findById(DOCUMENTO, ChiaviSalvate.class);
            return ChiaviVapid.da(altre.pubblica(), altre.privata());
        }
    }
}
