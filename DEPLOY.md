# Deploy in produzione

TrovaCampo gira **sullo stesso server di presenze**, e ne riusa quasi tutto:

| Cosa | Di chi è |
|---|---|
| Porte 80/443 e certificati Let's Encrypt | Caddy di presenze, blocco `trovacampo.footballer.it` in `deploy/Caddyfile` |
| Registro delle immagini (`127.0.0.1:5000`) | servizio `registro` di presenze |
| Rete Docker `presenze_default` | compose di presenze |
| MongoDB, backend Spring Boot, app Ionic | `docker-compose.prod.yml` di questo repository, in `~/trovacampo` sul server |

Il rilascio segue lo stesso schema: un runner self-hosted su **server2** (il
VPS Contabo che fa solo da macchina di build, per presenze e per TrovaCampo)
costruisce le due immagini, le spinge nel registro del server con un tunnel
SSH, poi ricrea i container. Il workflow è `.github/workflows/produzione.yml` e parte a ogni
push sul branch `produzione`, oppure a mano dalla scheda Actions.

```
Internet ─▶ Caddy (presenze) ─┬─ /api/*  ─▶ trovacampo-backend:3000 ─▶ trovacampo-mongo
                              └─ il resto ─▶ trovacampo-frontend:80 (nginx)
```

## Una volta sola

### 1. DNS

Un record `A` per `trovacampo.footballer.it`, con lo stesso IP del server di
presenze. Va fatto **prima** del passo 5: Caddy chiede il certificato appena
trova il blocco nuovo, e se il nome non risolve riprova con attese sempre più
lunghe.

### 2. Il file d'ambiente sul server

```bash
ssh UTENTE@SERVER
mkdir -p ~/trovacampo && cd ~/trovacampo
openssl rand -hex 24          # la password di Mongo
nano .env.prod                # vedi .env.prod.example
chmod 600 .env.prod
```

Servono due righe, `MONGO_USER` e `MONGO_PASSWORD`. La password va generata
con `-hex` e non `-base64`: finisce dentro un URI `mongodb://`, dove `/` e `+`
hanno un significato.

Sul server non c'è un clone di questo repository: il compose lo copia il
workflow a ogni rilascio.

### 3. Il runner

Un runner self-hosted su un account personale vale per **un solo**
repository, quindi quello di presenze non vede i job di TrovaCampo. Su server2
ce ne sono due, ognuno in una cartella sua: `presenze-build` e questo. Si
registra così:

**Settings → Actions → Runners → New self-hosted runner** di
`cardinal76/trovacampo`, seguendo le righe proposte, con l'etichetta:

```
trovacampo-build
```

e poi come servizio, perché sopravviva a un riavvio:

```bash
sudo ./svc.sh install && sudo ./svc.sh start
```

Due runner sulla stessa macchina aprono il tunnel verso il registro nello
stesso momento se due rilasci si accavallano: per questo TrovaCampo usa la
porta locale **5001** e presenze la 5000 (vedi `REGISTRO` nel workflow). Sul
server il registro è uno solo, sempre su 127.0.0.1:5000.

### 4. I segreti

In **Settings → Secrets and variables → Actions** di questo repository, gli
**stessi tre valori** di presenze:

- `PRODUZIONE_HOST`
- `PRODUZIONE_USER`
- `PRODUZIONE_SSH_KEY`

### 5. Il primo rilascio

Prima TrovaCampo, poi il Caddyfile di presenze: l'ordine inverso funziona
lo stesso, ma nel frattempo il sito risponde 502.

```bash
git checkout -B produzione origin/master && git push origin produzione
```

Il workflow controlla da sé che il registro e la rete di presenze ci siano e
che `.env.prod` esista, e lo dice in chiaro se manca qualcosa. Poi va
rilasciato presenze con il blocco `trovacampo.footballer.it` nel Caddyfile:
è il suo deploy a ricreare Caddy.

## Aggiornare

```bash
git checkout produzione && git merge --ff-only master && git push origin produzione
```

Le PR si uniscono **sempre in `master`**, mai direttamente in `produzione`:
`produzione` deve restare un passo indietro a `master` o uguale, così il
rilascio è un avanzamento veloce. Una PR unita in `produzione` vi aggiunge un
commit di merge che `master` non ha, e il comando qui sopra si ferma con
`fatal: Not possible to fast-forward`. Se succede, prima si porta `master` in
pari con `produzione`, che così riceve anche quello che era finito solo in
produzione, poi si rilascia come al solito. Il controllo `merge-base` evita il
push se `master` ha nel frattempo commit che `produzione` non ha: in quel caso
serve un merge normale di `master` in `produzione`.

```bash
git fetch origin
git merge-base --is-ancestor origin/master origin/produzione && git push origin origin/produzione:master
```

A mano sul server, per un rollback o con Actions ferma:

```bash
cd ~/trovacampo
sed -i 's|^TAG_IMMAGINI=.*|TAG_IMMAGINI=<commit>|' .env.prod
docker compose -p trovacampo -f docker-compose.prod.yml --env-file .env.prod up -d
```

Il commit deve essere uno già passato dal workflow: le immagini nel registro
ci sono solo per quelli, e la pulizia settimanale di presenze tiene le ultime
dieci versioni di ciascuna.

## I campi dall'anagrafica di presenze

presenze legge da solo i Comunicati Ufficiali del Comitato Lazio (organici,
gironi, calendari, programmi gare) e ne ricava un'anagrafica: ogni società,
le sue squadre con il campionato di ognuna, il campo dove gioca in casa.
TrovaCampo la legge dall'API pubblica di presenze, per la rete Docker
condivisa (`ANAGRAFICA_URL=http://presenze-backend:8080` in
`docker-compose.prod.yml`), senza passare da Caddy.

- **I campi** arrivano da soli alle 7:45, 13:45 e 19:45, mezz'ora dopo i giri
  di lettura di presenze (`ANAGRAFICA_SINCRONIZZAZIONE`). Ogni coppia
  società–campo è una riga, con le stesse regole di un file importato: stessa
  chiave, niente cancellazioni, coordinate di presenze se ci sono, altrimenti
  la geocodifica di TrovaCampo. Da `/admin/importazione`, «Controlla
  l'anagrafica di presenze» fa lo stesso subito, con prima il controllo.
- **I campionati** di una società non si copiano: la scheda li chiede a
  presenze quando si apre (`GET /api/societa/{id}/squadre`), una riga per
  squadra. Una società non ancora legata all'anagrafica (arrivata da un file
  o inserita a mano) si cerca per nome: se presenze ne ha una con lo stesso
  nome, o una sola che risponde a quel nome, il legame si salva e resta. Se
  presenze non risponde, la scheda si vede lo stesso, con un avviso al posto
  dei campionati.
- **Le prossime partite** su ogni campo arrivano dal calendario di presenze
  (`GET /api/pubblico/anagrafica/partite`): il backend chiede due settimane
  con una chiamata sola e le tiene cinque minuti, poi scheda
  (`GET /api/societa/{id}/partite`, due settimane, al massimo 30) e mappa
  (`GET /api/campi/partite`, una settimana, le prime 3 per campo) le leggono
  da lì. Una partita si abbina al campo per l'id dell'impianto in presenze
  (`anagraficaImpiantoId`, messo dalla sincronizzazione), mai per nome; il
  campo è quello di casa di chi ospita. I campi arrivati da un file o
  inseriti a mano l'id non ce l'hanno, e partite non ne mostrano finché la
  sincronizzazione non li riconosce (stessa società, stesso campo). Se
  presenze non risponde, scheda e mappa si vedono lo stesso, senza partite.
  Serve presenze con l'endpoint delle partite: si rilascia prima presenze.

Perché ci sia qualcosa da leggere, in presenze va accesa la lettura
automatica dei comunicati (da `/campionato`, vedi il suo DEPLOY.md).

## Le notifiche push

Dalla scheda **Notifiche** (quarta icona in basso) chi usa il sito accende,
senza login, due avvisi, spenti di partenza:

- **un'ora prima** delle partite delle squadre che segue (la campanella
  accanto a ogni squadra, nella sezione Campionati della scheda di una
  società);
- **mezz'ora prima** delle partite sui campi entro 5, 10 o 20 km dalla
  posizione salvata.

Come funziona, in breve:

- **Le chiavi VAPID** (la firma delle notifiche) il backend le crea da solo
  la prima volta che servono e le tiene in Mongo, collezione
  `configurazione`, documento `vapid`: non c'è niente da generare né da
  mettere in `.env.prod`, e il backup di Mongo le comprende. La privata non
  finisce nei log (c'è solo la pubblica, alla creazione). **Non vanno
  cancellate**: con chiavi nuove i browser già iscritti smettono di ricevere
  notifiche finché non riaprono il sito. Chi volesse fissarle a mano può
  usare `VAPID_PUBBLICA` e `VAPID_PRIVATA` (base64url, il formato di tutte le
  librerie Web Push), che vincono su Mongo.
- **Le iscrizioni** stanno in `iscrizioni_notifiche`: endpoint del servizio
  push, chiavi del browser, squadre seguite, e la posizione (arrotondata a
  un centinaio di metri) solo per chi ha acceso le partite vicine. Si
  rinfrescano a ogni apertura del sito e scadono da sole dopo un anno senza
  aperture (indice TTL); un 404/410 del servizio push le cancella subito.
- **Il giro** parte ogni cinque minuti (ora di Roma) e legge le partite dalla
  stessa cache di scheda e mappa: nessuna chiamata in più a presenze. Le
  notifiche mandate stanno in `notifiche_inviate` per due giorni, così
  nessuna parte due volte, nemmeno dopo un riavvio. Si spegne con
  `NOTIFICHE_ATTIVE=false` nell'ambiente del backend (le iscrizioni si
  raccolgono lo stesso).
- **La squadra seguita** si riconosce da società di presenze, campionato,
  ente e lettera della squadra (vuota per la prima, "B" per la seconda),
  non dall'id del girone che cambia ogni stagione. La lettera nelle partite
  arriva da presenze: si rilascia **prima presenze** (PR sull'anagrafica
  pubblica), poi TrovaCampo. Con un presenze vecchio si avvisa per qualunque
  squadra di quella società in quel campionato.
- **Il backend esce su Internet** verso i servizi push (Google, Mozilla,
  Apple, Microsoft), sulla 443: il container lo fa già, la rete `interna`
  non è isolata. Gli endpoint accettati sono solo di quei servizi
  (`trovacampo.notifiche.servizi-push` in `application.yml`).

Il sito è installabile (`manifest.webmanifest`), e il service worker
(`public/sw.js`) serve solo alle notifiche: niente cache, quindi non trattiene
versioni vecchie dopo un rilascio. `nginx.conf` del frontend lo serve con
`Cache-Control: no-cache` (senza, finirebbe nella regola dei `.js` con un anno
di cache) e dà al manifest il tipo `application/manifest+json`. Caddy li passa
così come sono.

I limiti, da sapere:

- su **iPhone e iPad** le notifiche arrivano solo al sito aggiunto alla
  schermata Home (Condividi → Aggiungi alla schermata Home), con iOS 16.4 o
  successivo; la pagina Notifiche lo spiega;
- un sito **non legge la posizione in sottofondo**: si aggiorna quando lo si
  apre, se il permesso c'è già;
- in **navigazione privata** Chrome non iscrive alle notifiche;
- l'**app Capacitor** non ha le push del web: la pagina rimanda al sito;
- gli orari sono quelli dei **programmi gare** letti da presenze: una partita
  spostata all'ultimo momento può arrivare con l'orario vecchio.

Per controllare dal server quante iscrizioni ci sono:

```bash
docker exec trovacampo-mongo sh -c 'mongosh --quiet -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin trovacampo --eval "db.iscrizioni_notifiche.countDocuments()"'
```

e nei log del backend una riga `Notifiche: N mandate, …` a ogni giro che ha
mandato qualcosa.

## Importare i campi

Società, impianti e indirizzi si caricano dalla pagina
**https://trovacampo.footballer.it/admin/importazione** (in home c'è il
pulsante "Importa campi", visibile a chi è già entrato come amministratore da
quel browser). Si possono caricare due tipi di file, e il server riconosce da
solo quale dei due è arrivato.

Il resto dell'app resta pubblico: solo questa pagina chiede il login.

### Il PDF di un Comunicato Ufficiale

Il PDF del programma gare, così come lo pubblica il comitato LND: per ogni
partita si prende il campo della **squadra di casa** (quella a sinistra), con
il nome dell'impianto, l'indirizzo (la riga sotto) e il comune.

- il fondo fra parentesi ("(SINTEX)", "(ERBA)") e la frazione ("ROMA
  (CAMILLUCCIA)" diventa "ROMA") restano fuori;
- la stessa squadra in più campionati sullo stesso campo conta una volta sola;
- le partite con il campo "DA DEFINIRE" finiscono fra gli scarti.

Il PDF deve avere il testo selezionabile, com'è quello del comitato: una
scansione non si legge. Le colonne si leggono per posizione, come nel
tabulato di oggi: se un comitato usasse un'impaginazione diversa, il controllo
lo dice con un "riga non nel formato del programma gare" invece di importare
dati sbagliati.

### Il file Excel

Il modello da compilare è
[`documenti/importazione/modello-importazione.xlsx`](documenti/importazione/modello-importazione.xlsx).

Conta solo il **primo foglio**. L'intestazione può stare anche sotto un titolo
(entro le prime dieci righe) e le colonne si riconoscono dal nome, senza badare
a maiuscole, accenti e punteggiatura:

| Colonna | Obbligatoria | Nomi riconosciuti |
|---|---|---|
| Società | sì | Società, Nome società, Denominazione, Squadra, Club |
| Impianto | sì | Impianto, Nome impianto, Campo, Nome campo, Campo di gioco, Stadio |
| Indirizzo | sì | Indirizzo, Indirizzo impianto, Indirizzo campo, Via |
| Comune | no | Località, Comune, Città, Paese |
| Provincia | no | Provincia, Prov, Sigla provincia |
| Latitudine / Longitudine | no | Lat/Latitudine, Lng/Lon/Longitudine |

Le colonne con altri nomi vengono ignorate, e l'esito le elenca: un "Indirizzo
del campo" scritto diversamente si scopre lì.

### Cosa succede alle righe

- una riga con **stessa società e stesso impianto** di una già presente la
  **aggiorna** invece di duplicarla, quindi lo stesso file ricaricato non
  raddoppia l'archivio;
- una **cella vuota non cancella** un valore già presente;
- se cambia l'indirizzo e il file non porta coordinate, quelle vecchie vengono
  tolte e ricalcolate;
- le righe senza coordinate le geocodifica il backend **in sottofondo, una al
  secondo** (il limite di Nominatim): mille righe sono circa venti minuti. Il
  comune migliora molto la precisione;
- le righe incomplete vengono scartate con il numero di riga e il motivo.

### Chi può importare

Il login è quello di **presenze**: stesso Keycloak, stesso utente e stessa
password. Per importare serve in più il ruolo di realm **`trovacampo-admin`**,
che non dà nessun permesso dentro presenze. Client e ruolo li crea nel realm
il deploy di presenze (`riconcilia-realm.py`); il ruolo però va **assegnato**
a mano, una volta per persona.

Dal server, senza aprire la console:

```bash
cd ~/presenze && set -a && . ./.env.prod && set +a
docker exec presenze-keycloak /opt/keycloak/bin/kcadm.sh config credentials \
  --server http://localhost:8080/auth --realm master \
  --user "$KEYCLOAK_ADMIN" --password "$KEYCLOAK_ADMIN_PASSWORD"
docker exec presenze-keycloak /opt/keycloak/bin/kcadm.sh add-roles \
  -r presenze --uusername NOME_UTENTE --rolename trovacampo-admin
```

Oppure dalla console di Keycloak (tunnel sulla 18081, vedi il `DEPLOY.md` di
presenze): realm **presenze** → **Users** → l'utente → **Role mapping** →
**Assign role** → `trovacampo-admin`.

Chi è già dentro deve **uscire e rientrare** dalla pagina: il ruolo compare
nel token solo al login successivo.

### Caricare

1. apri https://trovacampo.footballer.it/admin/importazione ed entra con
   l'utente di presenze;
2. scegli il file;
3. **Controlla il file**: il server lo legge senza salvare niente e mostra quante
   righe inserirebbe, aggiornerebbe o scarterebbe, e perché;
4. se l'esito torna, **Importa**. Il pulsante compare solo dopo un controllo
   riuscito sullo stesso file.

Chi importa finisce nel log del backend (`docker logs trovacampo-backend`),
con il nome del file e quante righe ha scritto.

## Creare, modificare, eliminare una scheda

Chi ha il ruolo `trovacampo-admin` vede il pulsante **Modifica** in cima alla
scheda di ogni società (dopo essere entrato almeno una volta da quel browser,
come per il pulsante dell'importazione). Il modulo contiene campo, anagrafica
e campionati; salvando si torna alla scheda aggiornata, e il backend annota nel
log chi ha modificato cosa.

Le coordinate: se cambi l'indirizzo lasciandole com'erano, o le svuoti, le
ricalcola la geocodifica automatica; se le correggi a mano, valgono quelle.

In home, all'amministratore, c'è anche **Nuova società**: lo stesso modulo,
vuoto. Per eliminare una società c'è il cestino rosso accanto a **Modifica**
nella scheda, e **Elimina società** in fondo al modulo di modifica, sempre con
una conferma: la società sparisce da ricerca, elenco e mappa e non si recupera
(se non da un backup di Mongo). Creazioni ed eliminazioni finiscono nel log
del backend con il nome di chi le ha fatte.

Una scheda che viene dall'anagrafica di presenze (ha `anagraficaSocietaId` o
`anagraficaImpiantoId`) tornerebbe alla sincronizzazione dopo: eliminandola
resta un'**esclusione** nella collezione Mongo `societa_escluse` (nome della
società e del campo compattati, come la chiave dell'importazione, più la
coppia di id di presenze, chi e quando). Importazione e sincronizzazione
saltano le righe escluse e le contano nell'esito. Un campo inserito a mano o
da un file si elimina e basta. In `/admin/importazione`, sotto, c'è l'elenco
dei **Campi esclusi** con **Riammetti** (`GET` e `DELETE
/api/admin/esclusioni/{id}`): il campo torna alla sincronizzazione
successiva, se presenze lo manda ancora.

## Memoria

Il server ha già Keycloak, Postgres e lo Spring Boot di presenze. Per questo
i container hanno un tetto (`mem_limit`), Mongo usa una cache di 256 MB invece
di mezza RAM della macchina, e la JVM si ferma al 75% del suo container: in
tutto TrovaCampo occupa poco più di 1 GB al massimo. Su un server da 4 GB ci
sta; su uno da 2 GB no.

## Backup

I dati sono tutti in Mongo:

```bash
docker exec trovacampo-mongo sh -c 'mongodump --quiet --archive -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --authenticationDatabase admin' \
  | gzip > ~/backup-trovacampo-$(date +%F).archive.gz
```

## L'app sul telefono

La build di produzione punta a `https://trovacampo.footballer.it`
(`mobile-ionic/src/environments/environment.prod.ts`), quindi la stessa
build serve sia il sito sia l'app Capacitor.
