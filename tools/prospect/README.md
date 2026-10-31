# Prospect: le ASD/SSD d'Italia su una mappa

Lista commerciale delle associazioni e società sportive dilettantistiche,
partendo dal calcio: collection MongoDB `prospect` (chiave il codice
fiscale), export `prospect_italia.xlsx` e una mappa con i filtri per regione,
disciplina e organismo. **Solo per chi amministra**: tutto sta sotto
`/api/admin/prospect` (ruolo di realm `trovacampo-admin`) e la pagina
`/admin/prospect` non è collegata dalle pagine pubbliche.

Il codice è nel backend (`backend-java/src/main/java/it/trovacampo/api/prospect`):
qui ci sono solo questo README e `prospect.sh`, che chiama gli stessi
endpoint da terminale.

## La fonte

**Registro nazionale delle attività sportive dilettantistiche (RASD)** di
Sport e Salute, parte pubblica: <https://registro.sportesalute.eu/#/registro/n/>.
La pagina «Cerca ASD/SSD nel Registro pubblico» legge un'API JSON, la stessa
che usa l'importazione:

| Cosa | Richiesta |
|---|---|
| elenco | `GET /api/istruttoria/lista/?chiamante=registroPubblico_n&istruttoria_societa_sedeLegale_regione_id_f=<id>&start=<n>&length=100` |
| dettaglio | `GET /api/istruttoria/<id>/sidebar/?chiamante=registroPubblico_n` |
| filtri disponibili | `OPTIONS /api/istruttoria/lista/?chiamante=registroPubblico_n` |

Senza almeno un filtro l'elenco risponde vuoto: si chiede **una regione alla
volta** (gli id delle regioni sono quelli del filtro, Lazio = 12). Verificato
il 30 ottobre 2026: il Lazio ha 12.070 iscritti, di cui 823 con
un'affiliazione FIGC attiva; una pagina da 100 righe arriva in 2-3 secondi.

- **Elenco**: codice fiscale, denominazione, regione, comune della sede,
  organismi affilianti attivi (come icone, `static/os/FIGC.png` → `FIGC`),
  natura giuridica, data di approvazione dell'iscrizione.
- **Dettaglio**: discipline attive, tesserati attivi, attività e
  partecipazioni dell'ultimo anno. Porta anche il legale rappresentante, che
  **non si legge** (vedi sotto).
- L'indirizzo non c'è, solo il comune della sede legale.

Il sito non ha un `robots.txt` (risponde 404) né una pagina di note legali
per il registro; il registro è pubblico per legge (D.Lgs. 39/2021).
Le pagine di Sport e Salute riportano «© Tutti i diritti sono riservati»: i
dati si usano per uso interno e citando la fonte, senza ripubblicare il
registro.

## Come si lancia

Dopo il rilascio, dalla pagina **/admin/prospect** (login con il Keycloak di
presenze): si sceglie il **dettaglio** (nessuno, solo affiliate FIGC, calcio,
tutte le società) e poi «Importa il Lazio» oppure «Importa tutta Italia».
L'importazione gira in sottofondo nel backend, al ritmo lento descritto sotto;
la pagina mostra la fase (in corso, in pausa per la finestra oraria o per il
tetto, ferma per un blocco, interrotta da un riavvio), la regione corrente, le
richieste di oggi e una stima del tempo che manca. «Ferma» la interrompe dopo
la richiesta (o durante la pausa) in corso, «Riprendi» la fa ripartire.

**Fuori dalla finestra oraria (di default 22:00-07:00) non parte subito**: si
mette in pausa da sola e comincia alle 22. Lanciarla di giorno va bene, la
pagina dice «In pausa: fuori dalla finestra oraria».

Da terminale, con un access token di Keycloak di un utente `trovacampo-admin`:

```bash
export TOKEN=...                       # client trovacampo-frontend
./prospect.sh prova                    # Lazio, due pagine: per vedere che funziona
./prospect.sh avvia 12                 # tutto il Lazio, dettaglio del calcio
DETTAGLI=figc ./prospect.sh avvia      # tutta Italia, dettaglio delle sole affiliate FIGC
DETTAGLI=tutte ./prospect.sh avvia     # tutta Italia, dettaglio di tutte (settimane)
./prospect.sh stato                    # fase, richieste di oggi, stima, ripresa per regione
./prospect.sh ferma
./prospect.sh riprendi                 # l'ultimo giro, con le sue opzioni, da dove era
./prospect.sh collega                  # di nuovo il collegamento ai campi
./prospect.sh export prospect_italia.xlsx
```

Gli endpoint (`POST /api/admin/prospect/importazione` accetta `regioni`,
anche ripetuto, `maxPagine`, `ricomincia`, `dettagli` = `nessuno` | `figc` |
`calcio` | `tutte`; `true` e `false` valgono come prima, `calcio` e `nessuno`):

| | |
|---|---|
| `POST /api/admin/prospect/importazione` | avvia, 202; 409 se ce n'è già una, 400 con regione o dettagli sconosciuti |
| `GET  /api/admin/prospect/importazione` | stato: fase, richieste di oggi, blocco, stima, ripresa per regione |
| `POST /api/admin/prospect/importazione/ferma` | ferma dopo la richiesta in corso |
| `POST /api/admin/prospect/importazione/riprendi` | riprende l'ultimo giro, 202; 409 se ce n'è già uno, 404 se non c'è niente |
| `POST /api/admin/indirizzi/impianti-open-data?fonte=cmrc\|veneto` | (re)importa un censimento degli impianti open data |
| `POST /api/admin/prospect/collegamento` | collega il calcio laziale ai campi LND e al censimento open data |
| `GET  /api/admin/prospect/copertura` | quanti hanno l'indirizzo, per fonte e confidenza |
| `POST /api/admin/prospect/geocodifica` | OpenStreetMap per chi manca, in sottofondo (`ritenta`, `limite`) |
| `GET  /api/admin/prospect/geocodifica` | a che punto è |
| `GET  /api/admin/prospect` | i prospect con le coordinate, per la mappa |
| `GET  /api/admin/prospect/export.xlsx` | `prospect_italia.xlsx` |

### Il dettaglio, regione per regione

Il dettaglio (discipline, tesserati, attività) si chiede in **tutte le regioni
del giro**, per le società dell'ambito scelto:

| `dettagli` | per chi | quante, in Italia |
|---|---|---|
| `nessuno` | nessuno: solo l'elenco | 0 |
| `figc` | le affiliate FIGC (dall'elenco) | circa 12.000 |
| `calcio` (default) | FIGC, o con una disciplina «Calcio…» già letta | circa 12-13.000 |
| `tutte` | tutte le società | oltre 100.000 |

Un dettaglio letto da meno di **30 giorni** (`dettaglio-valido-giorni`) non si
richiede: rilanciare un giro, o riprenderne uno fermato a metà pagina, non
rifà le chiamate già fatte. L'elenco del registro non si filtra per
organismo, quindi anche «solo FIGC» scorre tutte le pagine d'Italia (circa
1.000) per trovarle.

### Ripresa

Dopo ogni pagina si salva in `prospect_importazione` il prossimo `start` di
quella regione, e in `prospect_importazione_giro` le opzioni del giro, la fase,
il motivo di un blocco e il contatore delle richieste di oggi. Un'importazione
fermata, bloccata, interrotta da un errore o da un riavvio del backend (anche
per un rilascio) **non riparte da sola**: «Riprendi» (o `./prospect.sh
riprendi`) la rilancia con le stesse regioni e lo stesso dettaglio, dalla
pagina dove era; le regioni finite si saltano. Le pause per la finestra
oraria e il tetto, invece, finiscono da sole senza perdere la posizione.
`ricomincia=true` riparte da zero (senza cancellare niente: i prospect si
aggiornano per codice fiscale, e i dettagli recenti si saltano).

Se durante l'importazione il registro aggiunge iscritti, le pagine scorrono:
qualche società può saltare. Un secondo giro con `ricomincia=true` le
recupera, senza doppioni.

## Ritmo e tempi

Marco vuole mappare **tutta Italia** e va bene metterci giorni: la priorità è
**non farsi bloccare**, non la velocità. Le proprietà sono in
`backend-java/src/main/resources/application.yml`, sotto
`trovacampo.prospect.rasd`, e si cambiano con le variabili d'ambiente del
container (vedi `DEPLOY.md`):

| proprietà | variabile | default | cosa |
|---|---|---|---|
| `ritmo.pausa-ms` | `PROSPECT_RASD_PAUSA_MS` | 3000 | pausa fra due richieste (minimo 1000) |
| `ritmo.jitter-ms` | `PROSPECT_RASD_JITTER_MS` | 1000 | in più, a caso fra 0 e questo |
| `ritmo.finestra` | `PROSPECT_RASD_FINESTRA` | `22:00-07:00` | ore di lavoro, ora italiana; vuota = sempre |
| `ritmo.tetto-giornaliero` | `PROSPECT_RASD_TETTO` | 7000 | richieste per giorno di calendario; 0 = nessun tetto |
| `dettaglio-valido-giorni` | `PROSPECT_RASD_DETTAGLIO_GIORNI` | 30 | dettaglio recente, non si richiede |
| `intervallo-ms` | | 1100 | minimo del client HTTP, per qualunque chiamata |

Quindi **una richiesta ogni 3-4 secondi**, più il tempo della risposta (circa
un secondo per un dettaglio, 2-3 per una pagina di elenco): in media circa 4,5
secondi, **circa 800 l'ora**. Nelle nove ore della finestra ne stanno circa
7.200; il tetto le ferma a **7.000 per notte**. Fuori finestra il giro dorme e
riparte alle 22; raggiunto il tetto, riparte a mezzanotte (con la finestra di
default non succede quasi mai: ci si arriva verso le 7).

| giro | richieste | tempo, ai ritmi di default |
|---|---|---|
| Lazio, dettaglio del calcio | 121 pagine + circa 820 dettagli ≈ 950 | circa un'ora e un quarto, una notte |
| Italia, solo elenco | circa 1.000 pagine | circa un'ora e mezza, una notte |
| **Italia, solo calcio (FIGC)** | 1.000 pagine + circa 12.000 dettagli ≈ 13.000 | **circa 2 notti** |
| **Italia, tutto** | 1.000 pagine + oltre 100.000 dettagli ≈ 101.000 | **circa 15 notti** (14,4 giorni) |

Per «tutto» in circa una settimana si può allargare la finestra o alzare il
tetto, per esempio `PROSPECT_RASD_FINESTRA=20:00-08:00` e
`PROSPECT_RASD_TETTO=9500` (≈ 11 notti), oppure lavorare anche di giorno: è
meno prudente, meglio provarlo dopo il «solo calcio» andato liscio. La stima
nella pagina tiene conto di finestra e tetto, dai totali che il registro ha
già dato per regione (5.000 a regione per quelle non ancora toccate).

## Limiti e buone maniere

- **Ritmo lento** (`RitmoRasd`), vedi sopra; in più il client HTTP non fa mai
  due richieste a meno di un secondo (`LimitatoreRichieste`, `intervallo-ms`).
- **User-Agent identificato**: `TrovaCampo-prospect/0.1 (+https://trovacampo.footballer.it)`.
- **Niente aggiramenti**: una risposta 401, 403, 407, 429 o 503, o una
  pagina HTML al posto del JSON (un captcha, un WAF), **ferma l'importazione
  alla prima**, con fase `bloccata`, il motivo e l'ora salvati in Mongo e
  mostrati nella pagina, senza riprovare. I dettagli già letti della pagina
  restano. Ripartire è una decisione di una persona («Riprendi»). Non si
  usano browser finti, proxy o altri trucchi. Se succede, si scrive a Sport e
  Salute (registro@sportesalute.eu) prima di riprovare.
- Nessun giro programmato: parte solo a mano.

## GDPR

- Si conservano **solo dati della persona giuridica**: codice fiscale,
  denominazione, natura giuridica, regione e comune della sede, organismi,
  discipline, tesserati, attività, data di iscrizione; più il campo di gioco.
- **Il nome del legale rappresentante non si archivia.** Il dettaglio si
  legge per etichetta da un elenco chiuso (`RegistroRasd.VOCI_DETTAGLIO`):
  tutto il resto, legale rappresentante compreso, non esce dal client HTTP.
  Un test (`ImportazioneProspectTest`) controlla che il nome non finisca in
  quello che si salva.
- La lista è visibile solo a chi amministra, e non è esposta dalle API pubbliche.

## L'indirizzo del campo

In ordine di preferenza, con la fonte scritta in `fonteIndirizzo`:

1. **`CAMPO_TROVACAMPO` / `CAMPO_PRESENZE`**: il campo di gioco dai
   comunicati LND, cioè i campi di TrovaCampo e, in seconda battuta,
   l'anagrafica di presenze (`/api/pubblico/anagrafica/impianti`). Fatto per il
   Lazio (`CollegamentoCampi`).
2. **`OPEN_DATA`**: il «Censimento impianti sportivi di Roma e Provincia»
   della Città metropolitana (CC0), nel collegamento insieme ai campi LND; e
   gli impianti sportivi di 11 comuni del Veneto su dati.veneto.it (CC BY
   4.0), importati con `?fonte=veneto`.
3. **`GEOCODIFICA`**: un campo su OpenStreetMap per nome e comune (Nominatim,
   una richiesta al secondo, ODbL), in sottofondo, confidenza bassa.
4. Il Registro Imprese è a pagamento: **non si usa**.

Il registro non ha la matricola FIGC e i comunicati non hanno il codice
fiscale, quindi il collegamento è **per nome**: la denominazione ridotta alle
parole che la distinguono (via forma giuridica, sigle puntate, «calcio»,
anno di fondazione), con il comune a decidere fra più candidati. Nel dubbio
non si collega. `collegamento` dice quale regola ha deciso (`nome`,
`nome+comune`, `parole`, `parole+comune`, `parola+comune`, poi `open-data`,
`open-data+paese`, `osm-nome`, `osm-paese`) e `confidenzaIndirizzo` quanto
fidarsi: quelle «per parole» e tutte le BASSA sono le prime da ricontrollare
a mano. Fonti, regole, licenze e copertura misurata: **`INDIRIZZI_LAZIO.md`**.

**Copertura stimata per il Lazio** (prova del 30 ottobre 2026 sulle 823
società FIGC del registro, contro i 561 campi pubblici di TrovaCampo e
l'anagrafica di presenze): **circa il 54%** ha l'indirizzo del campo, il 52%
anche le coordinate. Il resto sono soprattutto società di calcio a 5 (che
giocano in palestre e palazzetti, non nei campi dei comunicati del
calcio a 11), enti multisport (gruppi sportivi militari, CUS) e società
assenti dai comunicati della stagione: per loro servono i punti 2 e 3.
Con la normalizzazione unica e il censimento open data la misura del 30
ottobre sale al **60,3%** senza rete: dettagli in `INDIRIZZI_LAZIO.md`.

## Attribuzione

- Società: Registro nazionale delle attività sportive dilettantistiche,
  Sport e Salute S.p.A. (parte pubblica).
- Campi: Comunicati Ufficiali LND, Comitato Regionale Lazio, tramite
  TrovaCampo e presenze.
- Impianti open data: Censimento impianti sportivi di Roma e Provincia,
  Città metropolitana di Roma Capitale (CC0 1.0); «Comune di X - Impianti
  sportivi», dati.veneto.it (CC BY 4.0), per Verona, Schio, Thiene, Valdagno,
  Malo, Monte di Malo, Isola Vicentina, Villaverla, Santorso, Zugliano e San
  Vito di Leguzzano.
- Mappa e geocodifica: © OpenStreetMap contributors, dati ODbL
  (<https://www.openstreetmap.org/copyright>).

L'export ha un foglio «Fonti» con le stesse righe.
