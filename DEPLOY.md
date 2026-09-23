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

A mano sul server, per un rollback o con Actions ferma:

```bash
cd ~/trovacampo
sed -i 's|^TAG_IMMAGINI=.*|TAG_IMMAGINI=<commit>|' .env.prod
docker compose -p trovacampo -f docker-compose.prod.yml --env-file .env.prod up -d
```

Il commit deve essere uno già passato dal workflow: le immagini nel registro
ci sono solo per quelli, e la pulizia settimanale di presenze tiene le ultime
dieci versioni di ciascuna.

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
vuoto. In fondo al modulo di modifica c'è **Elimina società**, con una
conferma: la società sparisce da ricerca, elenco e mappa e non si recupera
(se non da un backup di Mongo). Creazioni ed eliminazioni finiscono nel log
del backend con il nome di chi le ha fatte.

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
