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
presenze): «Importa il Lazio» oppure «Importa tutta Italia». L'importazione
gira in sottofondo nel backend; la pagina mostra a che punto è, e «Ferma» la
interrompe dopo la richiesta in corso.

Da terminale, con un access token di Keycloak di un utente `trovacampo-admin`:

```bash
export TOKEN=...                       # client trovacampo-frontend
./prospect.sh prova                    # Lazio, due pagine: per vedere che funziona
./prospect.sh avvia 12                 # tutto il Lazio
./prospect.sh avvia                    # tutta Italia, Lazio per primo
./prospect.sh stato                    # avanzamento e punto di ripresa per regione
./prospect.sh ferma
./prospect.sh collega                  # di nuovo il collegamento ai campi
./prospect.sh export prospect_italia.xlsx
```

Gli endpoint (`POST /api/admin/prospect/importazione` accetta `regioni`,
anche ripetuto, `maxPagine`, `ricomincia`, `dettagli`):

| | |
|---|---|
| `POST /api/admin/prospect/importazione` | avvia, 202; 409 se ce n'è già una |
| `GET  /api/admin/prospect/importazione` | stato |
| `POST /api/admin/prospect/importazione/ferma` | ferma dopo la richiesta in corso |
| `POST /api/admin/indirizzi/impianti-open-data?fonte=cmrc\|veneto` | (re)importa un censimento degli impianti open data |
| `POST /api/admin/prospect/collegamento` | collega il calcio laziale ai campi LND e al censimento open data |
| `GET  /api/admin/prospect/copertura` | quanti hanno l'indirizzo, per fonte e confidenza |
| `POST /api/admin/prospect/geocodifica` | OpenStreetMap per chi manca, in sottofondo (`ritenta`, `limite`) |
| `GET  /api/admin/prospect/geocodifica` | a che punto è |
| `GET  /api/admin/prospect` | i prospect con le coordinate, per la mappa |
| `GET  /api/admin/prospect/export.xlsx` | `prospect_italia.xlsx` |

### Ripresa

Dopo ogni pagina si salva in `prospect_importazione` il prossimo `start` di
quella regione. Un'importazione fermata, interrotta da un errore o da un
riavvio del backend riparte da lì al lancio successivo; le regioni finite si
saltano. `ricomincia=true` riparte da zero (senza cancellare niente: i
prospect si aggiornano per codice fiscale).

Se durante l'importazione il registro aggiunge iscritti, le pagine scorrono:
qualche società può saltare. Un secondo giro con `ricomincia=true` le
recupera, senza doppioni.

### Tempi

Una richiesta al secondo, più il tempo della risposta:

- **Lazio**: 121 pagine di elenco + circa 820 dettagli (solo calcio) ≈ 25-30 minuti;
- **Italia**: circa 1.000 pagine di elenco (stima: ≈ 100.000 società) ≈ 1 ora e mezza.

In questo giro il **dettaglio si chiede solo per il calcio del Lazio**: per
tutta Italia sarebbero centomila chiamate, più di un giorno.

## Limiti e buone maniere

- **Al massimo una richiesta al secondo** (`LimitatoreRichieste`):
  `trovacampo.prospect.rasd.intervallo-ms`, 1100 di default; sotto i 1000 non
  si scende anche configurandolo.
- **User-Agent identificato**: `TrovaCampo-prospect/0.1 (+https://trovacampo.footballer.it)`.
- **Niente aggiramenti**: una risposta 401, 403, 407, 429 o 503, o una
  pagina HTML al posto del JSON (un captcha, un WAF), **ferma l'importazione**
  con stato `bloccata` e il motivo, senza riprovare. Non si usano browser
  finti, proxy o altri trucchi. Se succede, si scrive a Sport e Salute
  (registro@sportesalute.eu) prima di riprovare.
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
