package it.trovacampo.api.notifiche;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

/**
 * Il registro in Mongo: un documento per invio, con la chiave come
 * {@code _id}. L'unicità dell'{@code _id} fa da lucchetto (il secondo insert
 * fallisce), e un indice TTL butta i documenti dopo due giorni, quando la
 * partita è passata da un pezzo.
 */
@Component
public class RegistroInviiMongo implements RegistroInvii {

    static final String COLLEZIONE = "notifiche_inviate";
    static final Duration CONSERVAZIONE = Duration.ofDays(2);

    @Document(collection = COLLEZIONE)
    record Invio(@Id String id, Instant inviataIl) {}

    private final MongoTemplate mongo;
    private final AtomicBoolean indiceCreato = new AtomicBoolean();

    public RegistroInviiMongo(MongoTemplate mongo) {
        this.mongo = mongo;
    }

    @Override
    public boolean prenota(String chiave) {
        assicuraIndice();
        try {
            mongo.insert(new Invio(chiave, Instant.now()));
            return true;
        } catch (DuplicateKeyException giaMandata) {
            return false;
        }
    }

    @Override
    public void annulla(String chiave) {
        mongo.remove(Query.query(Criteria.where("_id").is(chiave)), Invio.class);
    }

    /** Alla prima prenotazione e non all'avvio: il backend deve partire anche senza Mongo. */
    private void assicuraIndice() {
        // Creare due volte lo stesso indice non fa niente: basta non rifarlo a ogni invio.
        if (!indiceCreato.get()) {
            mongo.indexOps(Invio.class)
                    .createIndex(new Index().on("inviataIl", Sort.Direction.ASC).expire(CONSERVAZIONE));
            indiceCreato.set(true);
        }
    }
}
