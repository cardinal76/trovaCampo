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

1. **Importa impianti open data** (`./prospect.sh opendata [fonte]`, `POST
   /api/admin/indirizzi/impianti-open-data?fonte=cmrc|veneto`, nella pagina
   con la scelta «Censimento open data»): scarica un censimento e lo mette in
   `impianto_open_data`. `cmrc` (di default) è quello CC0 di Roma, una
   richiesta sola; `veneto` sono i comuni del Veneto, un file per comune a
   dieci secondi l'uno dall'altro (un paio di minuti). Si rifà quando l'ente
   aggiorna i file. Vedi «Gli altri censimenti» più sotto.
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
| `OPEN_DATA` | Impianti sportivi di 11 comuni del Veneto su [dati.veneto.it](https://dati.veneto.it) (Verona e Alto Vicentino) | **CC BY 4.0**: «Comune di X - Impianti sportivi, dati.veneto.it (CC BY 4.0)» |
| `GEOCODIFICA` | OpenStreetMap via Nominatim | **ODbL 1.0**, «© OpenStreetMap contributors» |

Le coordinate di un indirizzo open data (il censimento non ne ha) vengono
dalla geocodifica di TrovaCampo (`NominatimGeocoding`): anche loro ODbL.
L'attribuzione viaggia con ogni esito (`IndirizzoMigliore.attribuzione`), ed è
nel foglio «Fonti» dell'export e in fondo alla pagina. Per l'open data è quella
del dataset preciso, salvata in ogni impianto (`ImpiantoOpenData.attribuzione`,
per il Veneto il comune); gli impianti importati prima la ricavano dalla fonte.

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

#### Gli altri censimenti (31 ottobre 2026)

Stesso importatore (`ImportatoreImpiantiOpenData`, fonti in `FonteOpenData`),
stesso `LettoreCsv` (UTF-8 o Windows-1252, `;` o `,`), stessa normalizzazione
dei nomi e dei comuni per l'id e per l'abbinamento. Lo lancia l'admin a mano,
niente scheduler. Una risposta 401, 403, 407, 429, 503 o una pagina HTML al
posto del CSV ferma tutto (503 con il motivo), senza riprovare; un file che va
in timeout o risponde 404 finisce in `nonLetti` e gli altri si importano.
User-Agent quello di TrovaCampo (`trovacampo.nominatim.user-agent`).

**Veneto** (`?fonte=veneto`, sigla `veneto-comuni`). dati.veneto.it non ha un
censimento regionale degli impianti: ha i dataset di 11 comuni, tutti **CC BY
4.0**, in CSV dal datastore CKAN del portale (`/SpodCkanApi/api/3/datastore/dump/<id>.csv`,
elenco e URL in `application.yml`, `trovacampo.indirizzi.open-data.veneto`).
Le note legali del portale rimandano alla licenza di ogni scheda; il
robots.txt chiede `Crawl-delay: 10`, quindi dieci secondi fra un file e
l'altro (`OpenDataVeneto.intervallo`, sotto i dieci non si scende). Due
modelli: Verona (`DENOMINAZIONE EDIFICIO`, `INDIRIZZO`, `TIPOLOGIA ATTIVITA'
SPORTIVA`, coordinate, senza il comune) e quello dell'Alto Vicentino
(`Impianto_*`, `Latitudine`/`Longitudine`). Il secondo ha anche
`Impianto_Contatto` e `Impianto_Contatto_Email`, spesso di persone: **non si
leggono**, come tutte le colonne che non servono (un test lo controlla).

| Dataset (dati.veneto.it) | Aggiornato | Righe | Con calcio | Con coordinate |
|---|---|---:|---:|---:|
| Comune di Verona - Impianti sportivi | 2022-06-21 | 67 | 52 | 52 |
| Comune di Schio - Impianti Sportivi | 2021-08-26 | 29 | 11 | 11 |
| Comune di Thiene - Impianti Sportivi | 2021-07-22 | 30 | 15 | 0 |
| Comune di Malo - Impianti sportivi | 2021-07-26 | 12 | 6 | 0 |
| Comune di Monte di Malo - Impianti sportivi | 2021-07-27 | 4 | 4 | 0 |
| Comune di Isola Vicentina - Impianti Sportivi | 2021-11-19 | 10 | 5 | 0 |
| Comune di Villaverla - Impianti sportivi | 2022-05-20 | 7 | 4 | 4 |
| Comune di Santorso - Impianti Sportivi | 2022-05-20 | 4 | 2 | 2 |
| Comune di Zugliano - Impianti Sportivi | 2021-11-19 | 8 | 2 | 0 |
| Comune di San Vito di Leguzzano - Impianti Sportivi | 2022-05-20 | 2 | 1 | 1 |
| Comune di Valdagno - Impianti Sportivi | 2021-07-23 | non letto | | |
| **Totale** | | **173** | **102** (71 con un campo a 11) | **70** |

«Con calcio» conta anche palestre e piastre con il calcio a 5. Le discipline
prendono i nomi del censimento di Roma (Calcio-11, Calcio-5, Calcio-7): un
«calcio» senza numero in un campo o in uno stadio conta come campo a 11. Alcuni
file hanno perso lettere accentate e trattini già alla fonte («Via dei Bold?»):
i trattini si rimettono nei nomi, gli indirizzi restano come sono. Valdagno è
andato in timeout (60 secondi senza risposta) ed è rimasto fuori dalla prova;
è nella configurazione, e un'importazione dal server lo riprova.

Il collegamento automatico dei prospect (`CollegamentoCampi`) resta sul Lazio:
gli impianti veneti servono già a `LocalizzatoreSocieta.trova` e a `POST
/api/admin/indirizzi/trova`, e al collegamento quando si estenderà al Veneto.

**Richieste fatte il 30-31 ottobre 2026** (User-Agent
`TrovaCampo-opendata/0.1 (+https://trovacampo.footballer.it; ...)`, una
ogni 10 secondi, ogni file scaricato una volta sola e poi cancellato):

| Portale | Richieste | Esito |
|---|---:|---|
| dati.veneto.it | 28 | robots.txt, ricerca nel catalogo (prima di trovare l'API CKAN su `/SpodCkanApi`), note legali, 10 CSV; un timeout (Valdagno), nessun blocco |
| dati.comune.milano.it | 1 | il robots.txt risponde con la pagina «Accesso non consentito»: **fermi, da riprovare** |
| www.dati.gov.it | 1 | **403** (dal sito, non dal proxy): **fermi, da riprovare** |
| dati.regione.sicilia.it | 2 | due connessioni chiuse di fila: **fermi, da riprovare** |

Quindi **Comune di Milano** e **Regione Siciliana** non hanno un importatore:
licenza, formato e numeri dei loro dataset non si sono potuti verificare. Da
riprovare da un'altra rete (il server di produzione) o scrivendo ai portali.
Niente siti terzi non open data, niente motori di ricerca.

## OpenStreetMap

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
   Fuori dal Lazio: Milano e Sicilia da riprovare (sopra), altri comuni veneti
   (Padova, Venezia, Vicenza) se pubblicano su portali propri.
3. **Correzioni a mano** dall'amministrazione delle società di Roma senza
   campo: sono il grosso delle mancanti.
4. OpenStreetMap per il resto, con confidenza bassa.
