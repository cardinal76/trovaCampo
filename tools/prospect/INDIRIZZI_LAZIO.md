# Indirizzi delle società di calcio del Lazio

Il Registro RASD (registro.sportesalute.eu) dà codice fiscale, denominazione,
comune e organismi affilianti, ma non l'indirizzo. Il modulo
`backend-java/.../indirizzi` cerca **l'indirizzo del campo** di una società,
con coordinate, fonte e confidenza. Obiettivo: almeno il 70% delle società di
calcio del Lazio.

## Come si usa

Dal codice (per l'arricchimento della collection `prospect`):

```java
Optional<IndirizzoMigliore> trovato =
        localizzatoreSocieta.trova(new SocietaDaLocalizzare(denominazione, comune, codiceFiscale, codiceFigc));
// oppure, senza chiamare Nominatim:
localizzatoreSocieta.trova(societa, LocalizzatoreSocieta.Opzioni.SENZA_RETE);
```

Dall'amministrazione (token Keycloak con ruolo `trovacampo-admin`):

| Chiamata | Cosa fa |
|---|---|
| `POST /api/admin/indirizzi/impianti-open-data` | scarica e (re)importa il censimento open data in `impianto_open_data` (una richiesta) |
| `POST /api/admin/indirizzi/arricchimento?geocoding=true&ritenta=false&limite=` | cerca l'indirizzo delle società di calcio del Lazio in `prospect`, in sottofondo; 202 se parte, 200 con `avviato:false` e il motivo se `prospect` non esiste, 409 se ne gira già uno |
| `GET /api/admin/indirizzi/arricchimento` | a che punto è |
| `GET /api/admin/indirizzi/copertura` | totale, con indirizzo, con coordinate, per fonte e per confidenza |
| `POST /api/admin/indirizzi/trova?geocoding=false` | prova su una società: `{"denominazione":"A.S.D. ROMULEA","comune":"ROMA"}` |

Gli esiti vanno in una collection a parte, **`indirizzo_prospect`**, con
chiave il codice fiscale: l'arricchimento non scrive nei documenti di
`prospect`. Una società cercata e non trovata ha la riga con `fonte` nulla e
non si ricerca, salvo `ritenta=true` o una prima corsa fatta senza rete.

Ordine consigliato dopo il rilascio: importare il censimento, lanciare
l'arricchimento con `geocoding=false` (veloce, solo dati in Mongo), guardare
la copertura, poi rilanciarlo con `geocoding=true` per le mancanti (una
richiesta al secondo: qualche migliaio di società sono un'ora o due).

## Le fonti, in ordine

### 1. Campi di gioco LND (collection `societa`)

- **Cosa**: le coppie società–campo dei Comunicati Ufficiali del Comitato
  Regionale Lazio LND, sincronizzate dall'anagrafica di presenze
  (`/api/pubblico/anagrafica/impianti`) o importate dai file. Indirizzo e,
  quasi sempre, coordinate.
- **Licenza**: dati pubblicati nei Comunicati Ufficiali LND; già usati da
  TrovaCampo. Attribuzione: "Comunicati Ufficiali LND - Comitato Regionale
  Lazio".
- **Consistenza** (30 ottobre 2026): presenze conosce 713 società (699 tolte
  le righe di rinuncia/ripescaggio), **538 con il campo di casa** (77%), su
  393 impianti (375 con coordinate). In TrovaCampo 561 righe, 491 società,
  tutte con coordinate.
- **Abbinamento**: matricola FIGC se c'è (ALTA); nome sopra soglia nel
  comune della sede (ALTA da 0,9, MEDIA da 0,8); nome praticamente identico
  (≥ 0,95) di una sola società LND della regione con il campo in un altro
  comune **della stessa provincia** (MEDIA). Quest'ultima è l'unica eccezione
  alla regola dello stesso comune, ed è frequente: "A.S.D. ARNARA" gioca a
  Frosinone, "CASSINO CALCIO 1924" a Casalattico.

### 2. Censimenti open data degli impianti (collection `impianto_open_data`)

| Dataset | Ente | Licenza | Esito |
|---|---|---|---|
| [Censimento impianti sportivi di Roma e Provincia](https://opendata.cittametropolitanaroma.it/dataset/censimento-impianti-sportivi-di-roma-e-provincia) | Città metropolitana di Roma Capitale | **CC0 1.0** | **usato**. 6.636 righe (impianto × disciplina), 3.319 impianti, **1.007 con calcio** (Calcio-11, Calcio-5, Calcio 8-7-9) in 130 comuni, 911 righe a Roma. **Senza coordinate**: solo nome, comune, municipio, indirizzo. Dati del 2012 (XML generato il 28/12/2012, metadati aggiornati il 1/7/2025). Il catalogo CKAN scrive nelle URL delle risorse un IP interno (`172.16.1.68`): l'importatore usa la stessa risorsa sull'host pubblico (in `application.yml`, `trovacampo.indirizzi.open-data.cmrc-url`). |
| [Monitoraggio impianti sportivi](https://dati.lazio.it/dataset/4775d6b7-f1c0-4a8d-8129-1b41ea1e46d6) | Regione Lazio | CC BY 4.0 | **non scaricabile**: CSV e tracciato rispondono 500 (30/10/2026), datastore e SPARQL non lo hanno. Da riprovare: se torna, l'importatore va esteso alle sue colonne (attribuzione obbligatoria: "Regione Lazio"). |
| [Centri Sportivi Municipali 2018](https://dati.comune.roma.it/catalog/dataset/acb9bbf0-9beb-409d-b5e8-b4407dec1aef) | Roma Capitale | CC BY | **scartato**: 362 palestre scolastiche, nessun campo da calcio. |

Su dati.gov.it non risultano altri censimenti degli impianti per Lazio,
Latina, Frosinone, Viterbo o Rieti.

- **Abbinamento**: sempre nel comune della sede. Il nome dell'impianto, tolte
  le parole da impianto ("Centro Sportivo", "Stadio Comunale", "Parrocchia"),
  confrontato con quello della società: MEDIA da 0,9, BASSA da 0,8. Oppure,
  se la società porta solo il nome del paese ("A.S.D. CERVETERI") e nel
  comune c'è **un solo** impianto con campo a 11: BASSA. Mai per Roma.
- **Coordinate**: l'indirizzo si geocodifica con il geocoder di TrovaCampo
  (`NominatimGeocoding`, stessa pulizia degli indirizzi dei comunicati);
  l'attribuzione aggiunge "coordinate © OpenStreetMap contributors, ODbL".
- **Nessun dato di persone**: il file ha solo dati degli impianti.

### 3. OpenStreetMap via Nominatim (ultima risorsa)

- Due ricerche al massimo: "<nome senza forma giuridica> <comune>" e
  "campo sportivo <comune>", `format=jsonv2`, `countrycodes=it`, riquadro del
  Lazio (`bounded=1`). Si accettano solo `leisure=pitch`, `sports_centre`,
  `stadium` nel comune della sede. **Confidenza sempre BASSA.**
- **Usage policy** (operations.osmfoundation.org/policies/nominatim):
  massimo una richiesta al secondo, User-Agent identificato
  (`TrovaCampo/0.1 (+https://trovacampo.footballer.it; ...)`), niente uso
  massivo. Tutte le chiamate a Nominatim del backend (anche la geocodifica
  automatica dei campi) passano da `RitmoNominatim`, un intervallo unico di
  1,1 s (`trovacampo.nominatim.intervallo`).
- **Licenza**: ODbL 1.0. Attribuzione obbligatoria dove il dato si mostra:
  "© OpenStreetMap contributors". Viaggia nel campo `attribuzione` di ogni
  esito.
- Prove fatte (30/10/2026, cinque richieste a mano): "Romulea, Roma" trova
  `leisure=sports_centre` "SS Romulea", Via Farsalo 21; "campo sportivo
  Ronciglione" trova il Campo Sportivo "Marco Bigi"; "Arnara campo sportivo"
  e "Vicus Ronciglione" niente. Nessuna corsa di massa dal container.

## La normalizzazione dei nomi (`NomiSocieta`)

Prima del confronto: minuscolo, senza accenti e apostrofi; sigle puntate
unite ("A.S.D." → asd, "S.S.D. a r.l." → ssd + rl); tolte le forme
giuridiche (ASD, SSD, SSDARL, A.R.L., APD, ASDPOL, POL., Polisportiva,
Associazione Sportiva Dilettantistica, a responsabilità limitata...), le
parole generiche (calcio, football, club, F.C., sport), articoli e
preposizioni, gli anni di fondazione; "S." davanti a un nome diventa "san".

Somiglianza: coefficiente di Dice sulle parole rimaste, con un refuso
tollerato nelle parole lunghe; 1 se i nomi senza spazi coincidono
("TORBELLAMONACA" / "TOR BELLA MONACA"); almeno 0,85 se un nome di due o più
parole è tutto contenuto nell'altro; ×0,75 se i due nomi hanno anni di
fondazione diversi. **Soglia 0,8, sempre dentro lo stesso comune** (con la
chiave di `Comuni`: "Roma (RM)", "ROMA", "Lido di Ostia" sono lo stesso
comune). I casi reali sono in `NomiSocietaTest`.

## Copertura stimata

Il denominatore vero (le società di calcio del Lazio nel RASD) si saprà solo
quando `prospect` sarà importato: `GET /api/admin/indirizzi/copertura` lo
calcola. Stime al 30/10/2026:

- **Fonte 1 (LND)**: 538 società su 699 note a presenze, il **77% delle
  società con una squadra nei campionati LND del Lazio**. Sul RASD la quota è
  più bassa: il registro conta anche le società affiliate solo a enti di
  promozione (CSI, UISP, AICS, OPES...), di calcio a 5 e di sola scuola
  calcio, che nei comunicati LND non hanno un campo.
- **Fonte 2 (open data)**: con il prototipo della normalizzazione circa 60
  società LND hanno un impianto del censimento con lo stesso nome; il
  guadagno vero è sulle società di Roma e provincia fuori dai campionati LND
  (amatoriali, EPS) e sui paesi con un solo campo comunale. Copre solo la
  provincia di Roma.
- **Fonte 3 (OSM)**: da misurare con la corsa vera; resa attesa bassa nei
  piccoli comuni (pochi campi con nome su OSM), confidenza sempre bassa.

Per arrivare al 70% sul RASD servono probabilmente: il dataset della Regione
Lazio (se torna scaricabile, copre tutte e cinque le province), la matricola
FIGC nel `prospect` (abbinamento esatto con i campi LND) e la sede legale
delle società, quando il registro o un'altra fonte la daranno.
