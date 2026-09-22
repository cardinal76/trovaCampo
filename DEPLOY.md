# Deploy in produzione

TrovaCampo gira **sullo stesso server di presenze**, e ne riusa quasi tutto:

| Cosa | Di chi è |
|---|---|
| Porte 80/443 e certificati Let's Encrypt | Caddy di presenze, blocco `trovacampo.footballer.it` in `deploy/Caddyfile` |
| Registro delle immagini (`127.0.0.1:5000`) | servizio `registro` di presenze |
| Rete Docker `presenze_default` | compose di presenze |
| MongoDB, backend Spring Boot, app Ionic | `docker-compose.prod.yml` di questo repository, in `~/trovacampo` sul server |

Il rilascio segue lo stesso schema: un runner self-hosted a casa costruisce le
due immagini, le spinge nel registro del server con un tunnel SSH, poi ricrea
i container. Il workflow è `.github/workflows/produzione.yml` e parte a ogni
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
repository, quindi quello di presenze non vede i job di TrovaCampo. Se ne
registra un secondo, anche sulla stessa macchina di casa, in una cartella
sua:

**Settings → Actions → Runners → New self-hosted runner** di
`cardinal76/trovacampo`, seguendo le righe proposte, con l'etichetta:

```
trovacampo-build
```

e poi come servizio, perché sopravviva a un riavvio:

```bash
sudo ./svc.sh install && sudo ./svc.sh start
```

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
