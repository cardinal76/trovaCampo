# Indirizzi delle società di calcio del Lazio

Il Registro RASD dà codice fiscale, denominazione, comune della sede e
organismi affilianti, ma non l'indirizzo. Qui si cerca **l'indirizzo del campo
di gioco** delle società di calcio del Lazio in `prospect` (vedi `README.md`),
con coordinate, fonte e confidenza. Obiettivo: almeno il 70%.

Il codice: `backend-java/.../indirizzi` (fonti, normalizzazione dei nomi,
`LocalizzatoreSocieta`) e `backend-java/.../prospect` (`CollegamentoCampi`,
`ArricchimentoProspect`, che scrivono l'esito nei documenti `prospect`).

## Come si lancia

Tutto dall'amministrazione, `/admin/prospect` (riquadro «Indirizzi del calcio
nel Lazio»), o con `prospect.sh`; serve il ruolo `trovacampo-admin`.

1. **Importa impianti open data** (`./prospect.sh opendata`, `POST
   /api/admin/indirizzi/impianti-open-data`): scarica il censimento CC0 e lo
   mette in `impianto_open_data`. Una richiesta sola; si rifà quando l'ente
   aggiorna il file.
2. **Collega ai campi** (`./prospect.sh collega`, `POST
   /api/admin/prospect/collegamento`): campi LND e open data, subito, senza
   chiamare Nominatim. Parte da solo anche a fine importazione del Lazio.
3. **Cerca su OpenStreetMap** (`./prospect.sh geocodifica [limite]`, `POST
   /api/admin/prospect/geocodifica?ritenta=false&limite=`): in sottofondo, per
   chi è ancora senza indirizzo o senza posizione. Fino a tre richieste a
   Nominatim per società, una al secondo: le circa 330 mancanti del Lazio sono
   una ventina di minuti. Una società già cercata così non si ricerca, salvo
   `ritenta=true`. Senza prospect del Lazio risponde 200 con il messaggio
   «Nessun prospect di calcio del Lazio: va prima importato il Registro RASD».
4. **Copertura** (`./prospect.sh copertura`, `GET
   /api/admin/prospect/copertura`): totale, con indirizzo, con coordinate,
   percentuale, per fonte e per confidenza.

Per provare una società sola: `POST /api/admin/indirizzi/trova?geocoding=false`
con `{"denominazione":"A.S.D. ROMULEA","comune":"ROMA"}`.

Nel documento `prospect` finiscono `indirizzo`, `comuneCampo`, `nomeImpianto`,
`lat`/`lng`, `fonteIndirizzo`, `confidenzaIndirizzo`, `collegamento` (la
regola) e `geocodificaTentata`. Una fonte migliore prende il posto di una
peggiore, mai il contrario.

Dal codice, per chi deve localizzare una società fuori dai prospect:

```java
Optional<IndirizzoMigliore> trovato =
        localizzatoreSocieta.trova(new SocietaDaLocalizzare(denominazione, comune, codiceFiscale));
localizzatoreSocieta.trova(societa, LocalizzatoreSocieta.Opzioni.SENZA_RETE); // senza Nominatim
```

## Le fonti, in ordine

| Fonte (`fonteIndirizzo`) | Dati | Licenza / attribuzione |
|---|---|---|
| `CAMPO_TROVACAMPO` | schede di TrovaCampo: Comunicati Ufficiali LND, file importati, correzioni a mano | Comunicati Ufficiali LND - Comitato Regionale Lazio |
| `CAMPO_PRESENZE` | anagrafica di presenze, `/api/pubblico/anagrafica/impianti` (393 impianti, 538 società con il campo) | Comunicati Ufficiali LND - Comitato Regionale Lazio |
| `OPEN_DATA` | [Censimento impianti sportivi di Roma e Provincia](https://opendata.cittametropolitanaroma.it/dataset/censimento-impianti-sportivi-di-roma-e-provincia), Città metropolitana di Roma Capitale | **CC0 1.0** (citiamo comunque la fonte) |
| `GEOCODIFICA` | OpenStreetMap via Nominatim | **ODbL 1.0**, «© OpenStreetMap contributors» |

Le coordinate di un indirizzo open data (il censimento non ne ha) vengono
dalla geocodifica di TrovaCampo (`NominatimGeocoding`): anche loro ODbL.
L'attribuzione viaggia con ogni esito (`IndirizzoMigliore.attribuzione`), ed è
nel foglio «Fonti» dell'export e in fondo alla pagina.

### Il censimento open data

6.636 righe (impianto × disciplina), 3.319 impianti, **1.007 con calcio**
(Calcio-11, Calcio-5, Calcio 8-7-9) in 130 comuni della provincia di Roma.
Solo nome, comune, municipio e indirizzo: **niente coordinate**, nessun dato
di persone. Dati del 2012 (l'XML dice «generated 2012-12-28»), metadati
aggiornati a luglio 2025. Il catalogo CKAN scrive nelle URL delle risorse un
IP interno (`172.16.1.68`): l'importatore usa la stessa risorsa sull'host
pubblico (`trovacampo.indirizzi.open-data.cmrc-url` in `application.yml`).

Cercati e scartati (30 ottobre 2026):

- **Regione Lazio**, «Monitoraggio impianti sportivi» (dati.lazio.it, CC BY
  4.0): il CSV e il tracciato rispondono 500, il datastore e lo SPARQL non lo
  hanno. È l'unico che coprirebbe tutte e cinque le province: da riprovare.
- **Roma Capitale**, «Centri Sportivi Municipali 2018» (CC BY): 362 palestre
  scolastiche, nessun campo da calcio.
- Su dati.gov.it nient'altro per Lazio, Latina, Frosinone, Viterbo, Rieti.

### OpenStreetMap

Due ricerche al massimo per società, `format=jsonv2`, `countrycodes=it`,
riquadro del Lazio (`bounded=1`):

1. «<nome senza forma giuridica> <comune>» (regola `osm-nome`);
2. «campo sportivo <comune>» (`osm-paese`), **non a Roma**, dove un campo
   qualunque fra centinaia non è l'indirizzo di nessuno.

Si accettano solo `leisure=pitch|sports_centre|stadium` nel comune della
sede, e non quelli che nel nome dicono un altro sport (rugby, tennis,
basket...). **Confidenza sempre BASSA.** Usage policy: una richiesta al
secondo per tutto il backend (`RitmoNominatim`, che usa lo stesso
`LimitatoreRichieste` del registro), User-Agent
`TrovaCampo/0.1 (+https://trovacampo.footballer.it; ...)`, nessuna corsa
automatica.

## Normalizzazione e regole

Una sola normalizzazione, `indirizzi.NomiSocieta`, per tutte le fonti:
minuscolo, senza accenti e apostrofi; sigle puntate unite («A.S.D.» → asd,
«S.S.D.R.L.» → ssdrl); via forme giuridiche e loro troncamenti del registro
(ASD, SSD a r.l., APD, ASDPOL, POL., Polisportiva, «ASS.SPORTIVA DILETTANT.»,
«a responsabilità limitata», «in forma abbreviata»...), parole generiche
(calcio, football, club, F.C., C5), articoli, anni di fondazione. Il comune
passa da `Comuni` («Roma (RM)», «Lido di Ostia» → roma) prima di diventare
una chiave come i nomi.

- **Campi LND** (`FonteCampiLnd`, le regole della #69): stesse parole
  distintive (`nome`, `nome+comune`, ALTA); parole dell'una tutte nell'altra
  (`parole`, `parole+comune`) o una parola sola nello stesso comune
  (`parola+comune`), MEDIA. Nel dubbio non si collega.
- **Open data** (`FonteImpiantiOpenData`): sempre nel comune della sede.
  Somiglianza di Dice fra le parole della società e quelle del nome
  dell'impianto senza «Centro Sportivo», «Stadio Comunale», «Parrocchia»:
  soglia 0,8, MEDIA da 0,9. Un refuso è tollerato nelle parole lunghe, ma un
  nome «contenuto» nell'altro vuole parole identiche. Oppure la società porta
  solo il nome del paese e nel paese c'è un solo campo a 11
  (`open-data+paese`, BASSA).

I casi reali sono nei test: `NomiSocietaTest`, `FonteCampiLndTest`,
`CollegamentoCampiTest`, `FonteImpiantiOpenDataTest`, `RicercaCampiOsmTest`.

## Copertura misurata (30 ottobre 2026)

Sulle **823 società con affiliazione FIGC del Lazio** nel RASD (elenco
pubblico letto a una richiesta al secondo, solo per la misura e poi
cancellato), contro i 561 campi pubblici di TrovaCampo, l'anagrafica di
presenze e il censimento, senza rete:

| Fonte | Società | % | Cumulato |
|---|---:|---:|---:|
| `CAMPO_TROVACAMPO` | 433 | 52,6% | 52,6% |
| `CAMPO_PRESENZE` | 37 (5 senza coordinate) | 4,5% | 57,1% |
| `OPEN_DATA` | 26 (17 MEDIA, 9 BASSA) | 3,2% | **60,3%** |
| `GEOCODIFICA` | stima | ~2-5% | ~62-65% |

- La #69 misurava ~54% con le sue regole: la normalizzazione unica (che
  riconosce più troncamenti del registro, «DILETTANT.», «SOC.», «C5»...) porta
  i campi LND al 57,1% con le stesse regole.
- OpenStreetMap: provate 7 società mancanti (14 richieste, una al secondo).
  Una trovata in modo plausibile (LEGIO COLLEFERRO → Campo Sportivo Cedorelli,
  Colleferro), due sbagliate (un campo da rugby ad Aprilia, un campo qualunque
  a Roma) che hanno portato ai due filtri sopra, quattro niente. Resa bassa e
  confidenza bassa: va guardato a mano.
- Le 327 mancanti: 125 a Roma, 57 con «C5», «futsal» o «calcio a 5» nel nome
  (giocano in palestre e palazzetti, che i comunicati del calcio a 11 non
  hanno), più enti multisport, scuole calcio e società senza squadre nei
  comunicati della stagione.

## Cosa manca per il 70%

Servono circa 80 società in più (576 su 823). In ordine di resa:

1. **Il calcio a 5 nei comunicati**: il Comitato Lazio pubblica comunicati
   anche per il calcio a 5; se presenze li leggesse, i loro campi entrerebbero
   nella fonte 1 come quelli del calcio a 11.
2. **Il dataset della Regione Lazio**, quando torna scaricabile: copre tutte e
   cinque le province, il censimento della Città metropolitana solo Roma.
3. **Correzioni a mano** dall'amministrazione delle società di Roma senza
   campo: sono il grosso delle mancanti.
4. OpenStreetMap per il resto, con confidenza bassa.
