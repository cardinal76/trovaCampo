# TrovaCampo (Ionic + Angular)

Versione dell'app TrovaCampo con **Ionic 8 + Angular 20 standalone + Capacitor**, alternativa all'app Expo/React Native in [`../mobile`](../mobile). Copre le stesse funzioni e parla con la stessa API.

## Schermate

| Rotta          | Schermata      | Funzione                                                     |
| -------------- | -------------- | ------------------------------------------------------------ |
| `/`            | Cerca Campo    | Funzione 1: titolo, casella di ricerca e pulsante "Vai"       |
| `/risultati?q=`| Risultati      | Funzione 1: mappa dei campi trovati ed elenco dei risultati   |
| `/societa/:id` | Scheda società | Funzioni 2 e 3: anagrafica e campionati                       |
| `/aggiungi`    | Aggiungi campo | Segnalazione di un campo mancante                             |

La mappa usa **Leaflet** con le tile di OpenStreetMap: nessuna chiave API e nessun SDK proprietario. I pin sono `divIcon` disegnati in CSS, così non servono le immagini di Leaflet, che i bundler non risolvono. Le tile di OSM hanno una [usage policy](https://operations.osmfoundation.org/policies/tiles/) che vieta l'uso massivo: per un'app pubblicata serve un provider di tile proprio.

Le società senza coordinate (per esempio i campi appena segnalati, se la geocodifica non ha riconosciuto l'indirizzo) non compaiono sulla mappa ma restano nell'elenco, marcate con "senza posizione".

## Avvio

Serve l'API in ascolto: il backend Spring Boot in [`../backend-java`](../backend-java) oppure quello Node in [`../backend`](../backend) — espongono lo stesso contratto REST.

```bash
cd mobile-ionic
npm install
npm start            # http://localhost:4200
```

L'indirizzo dell'API si cambia in `src/environments/environment.ts` (default `http://localhost:3000`). Su dispositivo o emulatore `localhost` è il telefono stesso: va messo l'IP della macchina che esegue il backend.

## App nativa (Capacitor)

```bash
npm run build
npx cap add android          # oppure: npx cap add ios
npx cap sync
npx cap open android
```

I progetti nativi `android/` e `ios/` non sono versionati: si rigenerano con `cap add`.

## Test

```bash
npm test                     # ng test
```

Servono Chrome/Chromium: `karma.conf.js` usa un launcher headless senza sandbox, così i test girano anche in container e in CI. Se il browser non viene trovato, indicarlo con `CHROME_BIN`.
