# TrovaCampo API (Spring Boot)

API di TrovaCampo in Java 21 / Spring Boot 3.5 con MongoDB, alternativa al backend Node in [`../backend`](../backend). Espone lo **stesso contratto REST**, quindi i client (app Ionic e app Expo) funzionano indifferentemente con l'una o con l'altra.

## Endpoint

| Metodo | Percorso            | Descrizione                                                        |
| ------ | ------------------- | ------------------------------------------------------------------ |
| `GET`  | `/api/societa?nome=`| Funzione 1: cerca per nome società, nome impianto, indirizzo o località |
| `GET`  | `/api/societa/{id}` | Funzioni 2 e 3: anagrafica e campionati della società               |
| `POST` | `/api/societa`      | Inserisce un campo (`nomeSocieta`, `nomeImpianto`, `indirizzoImpianto`) |

Gli errori hanno la forma `{"errore": "..."}`: `404` per una società inesistente, `400` se mancano i dati obbligatori dell'inserimento.

## Come funziona la ricerca

Ogni società porta con sé il campo interno `testoRicerca`: la copia in minuscolo e senza accenti di nome società, impianto, indirizzo e località. La ricerca normalizza allo stesso modo il termine digitato e lo cerca lì con una regex, così "Città" trova "citta" e viceversa. I metacaratteri del termine (`.`, `(`, `|` …) vengono neutralizzati prima di finire nella query, quindi cercare `Almas Roma S.r.l.` è sicuro. Il campo non compare nelle risposte dell'API.

All'inserimento l'indirizzo viene geocodificato con [Nominatim/OpenStreetMap](https://nominatim.openstreetmap.org/) (gratuito, nessuna chiave API). Se il servizio non risponde o non riconosce l'indirizzo, il campo viene comunque salvato senza coordinate: comparirà fra i risultati ma non come pin sulla mappa. Nominatim ha una usage policy restrittiva (max 1 richiesta al secondo, niente uso massivo): per volumi alti serve un provider a pagamento o un'istanza self-hosted.

## Avvio

Serve un MongoDB in ascolto. Con Docker:

```bash
cd backend-java
docker compose up -d          # MongoDB su localhost:27017
./mvnw spring-boot:run        # oppure: mvn spring-boot:run
```

L'API resta in ascolto su <http://localhost:3000> (stessa porta del backend Node, quindi vanno avviati uno alla volta).

Al primo avvio, se la collezione è vuota, vengono caricate le società di esempio (le stesse di `backend/src/data/societa.ts`): solo "Certosa Calcio" e "Almas Roma S.r.l." hanno anagrafica e campionati completi, per mostrare anche il caso dei dati mancanti. I campi aggiunti dagli utenti non vengono mai sovrascritti.

## Configurazione

| Proprietà                    | Variabile d'ambiente | Default                                    |
| ---------------------------- | -------------------- | ------------------------------------------ |
| `spring.data.mongodb.uri`    | `MONGODB_URI`        | `mongodb://localhost:27017/trovacampo`     |
| `server.port`                | `PORT`               | `3000`                                     |
| `trovacampo.cors.origini`    | —                    | `*`                                        |
| `trovacampo.nominatim.url`   | —                    | `https://nominatim.openstreetmap.org`      |
| `trovacampo.seed.abilitato`  | —                    | `true`                                     |

## Test

```bash
mvn test
```

I test coprono la normalizzazione della ricerca, l'inserimento con e senza geocodifica riuscita e il contratto REST (`@WebMvcTest`), e non richiedono MongoDB.
