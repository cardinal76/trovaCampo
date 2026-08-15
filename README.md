# TrovaCampo

TrovaCampo è il progetto (in fase di analisi e progettazione) di un'app mobile pensata per aiutare chiunque sia legato al mondo del calcio dilettantistico a **trovare rapidamente il campo di gioco** di una società sportiva, semplicemente inserendone il nome.

## L'idea

Nel calcio dilettantistico e giovanile italiano ci sono decine di migliaia di giocatori, e trovare l'indirizzo esatto del campo dove si gioca una partita non è sempre semplice. TrovaCampo nasce per risolvere questo problema in modo semplicissimo: si digita il nome della società (es. "Atletico 400-Tor di Pippo") e l'app mostra su una mappa dove si trova il suo campo.

È pensata per:
- **genitori** che vogliono seguire i figli alle partite;
- **arbitri e osservatori arbitrali** dilettanti che devono raggiungere il campo di gara;
- **osservatori giovanili/scout** interessati a seguire le partite in una determinata zona.

## Funzionalità

Le funzionalità principali (vedi [`funzioni.docx`](funzioni.docx)) sono:

1. **Ricerca campo** – inserendo il nome della società in una maschera di ricerca, l'app mostra su una mappa geografica la posizione del campo.
2. **Ricerca società** – restituisce le informazioni anagrafiche della società cercata: presidente, indirizzo sede amministrativa, indirizzo campo, contatti, sito web, eventuale scuola calcio e relativi prezzi.
3. **Ricerca campionati società** – mostra a quali campionati partecipa la società trovata, distinguendo tra scuola calcio e settore agonistico.

### Sviluppi futuri

Come descritto nei documenti di analisi, alcune idee per evoluzioni successive dell'app includono:
- inserimento dei risultati delle partite da parte delle società, con classifiche aggiornate quasi in tempo reale;
- una mappa delle partite in corso nelle vicinanze, utile per osservatori e scout giovanili.

## Struttura del repository

```
├── backend/                                API di ricerca (Node.js + TypeScript + Express)
├── mobile/                                 App mobile (Expo + React Native + TypeScript)
├── funzioni.docx                          Analisi delle funzioni dell'app
├── evoluzioni.docx                        Documento (in bozza) delle evoluzioni future
├── bancaDati.docx                         Documento (in bozza) della banca dati
├── documenti/
│   ├── analisi/
│   │   ├── analisi_0.1.docx               Analisi dei requisiti - beta 0.1
│   │   └── analisi_0.2.docx               Analisi dei requisiti - beta 0.2
│   ├── progettazione/
│   │   └── progettazione_0.1.docx         Documento di progettazione - beta 0.1
│   ├── NoBet-Mockup-explanation.pdf        Mockup di riferimento (progetto NoBet)
│   └── nobet-new-mockup.pdf                Mockup di riferimento (progetto NoBet)
└── grafica/
    ├── home.jpg                            Schermata iniziale "Cerca Campo"
    ├── FunzioneUnoSchermataUno.jpg          Funzione 1 - ricerca per nome società
    ├── FunzioneUnoSchermataDue.jpg          Funzione 1 - risultato su mappa
    └── anagraficaSocietà.jpg                Esempio di scheda anagrafica società
```

> Nota: i PDF `NoBet-Mockup-explanation.pdf` e `nobet-new-mockup.pdf` appartengono a un altro progetto ("NoBet", un'app di pronostici sportivi con crediti virtuali) e sono conservati come riferimento/ispirazione per l'interfaccia, non fanno parte delle funzionalità di TrovaCampo.

## Stato del progetto

È stata avviata l'implementazione della **Funzione 1 (Ricerca campo)**:
- `backend/` espone un'API REST che cerca le società per nome campo/società/indirizzo su un set di dati di esempio (in attesa dell'importazione della banca dati reale, vedi `documenti/analisi/analisi_0.1.docx`), e permette di inserire nuovi campi.
- `mobile/` è un'app Expo/React Native con:
  - la schermata di ricerca ("Cerca Campo") e la schermata mappa che mostra i campi trovati, coerenti con i mockup in `grafica/`:
    - la Home ha il titolo "Cerca Campo", la casella di ricerca per nome società o indirizzo e il pulsante **"Vai"** (come in `grafica/FunzioneUnoSchermataUno.jpg`); si può cercare anche con il tasto invio della tastiera;
    - la schermata dei risultati mostra la mappa con i campi trovati e, sotto, l'**elenco completo dei risultati** (compresi quelli già geolocalizzati, segnalando con l'etichetta "senza posizione" quelli di cui non si conoscono ancora le coordinate); toccando una voce o il fumetto di un pin si apre la scheda società;
    - in cima ai risultati resta una barra di ricerca compatta per **correggere la ricerca sul posto**, senza tornare alla Home;
    - se la ricerca non dà risultati viene proposto di aggiungere il campo, mentre in caso di errore di rete c'è un pulsante "Riprova" che rilancia la stessa ricerca.
  - la schermata **"Aggiungi campo"**, raggiungibile dalla Home, per inserire un nuovo campo con nome campo, nome società e indirizzo. I tre campi sono normali caselle di testo: si può digitare oppure dettare con il microfono già presente sulla tastiera di iOS/Android, senza bisogno di librerie aggiuntive.
- All'inserimento, il backend prova a **geocodificare automaticamente l'indirizzo** tramite [Nominatim/OpenStreetMap](https://nominatim.openstreetmap.org/) (gratuito, nessuna chiave API) per ottenere le coordinate del campo. Se il servizio non trova l'indirizzo o non risponde, il campo viene comunque salvato senza coordinate e compare in ricerca nella lista "non ancora geolocalizzati" invece che come pin sulla mappa. Nominatim ha una usage policy restrittiva (max 1 richiesta al secondo, niente uso massivo): per un volume alto di inserimenti servirebbe un provider a pagamento o un'istanza self-hosted.

Sono state implementate anche le **Funzioni 2 (Ricerca società) e 3 (Ricerca campionati società)**, unite in un'unica schermata come nel mockup `grafica/anagraficaSocietà.jpg`:
- toccando un pin sulla mappa (tasto "Vedi scheda società" nel callout) o una voce della lista "non ancora geolocalizzati" nella schermata dei risultati, si apre la schermata **"Scheda società"**;
- mostra l'anagrafica (matricola, comitato regionale, presidente, indirizzo sede, contatti, sito web, presenza ed eventuali prezzi della scuola calcio) e i campionati a cui la società partecipa, distinti in agonistica e scuola calcio;
- se una società non ha ancora l'anagrafica completa (es. inserita manualmente con la sola Funzione 1), la scheda lo segnala esplicitamente invece di mostrare campi vuoti.

Nei dati di esempio, solo "Certosa Calcio" e "Almas Roma S.r.l." hanno l'anagrafica e i campionati compilati, per mostrare anche il caso di dati mancanti.

## Come avviare il progetto in locale

### Backend

```bash
cd backend
npm install
npm run dev      # avvia l'API su http://localhost:3000
```

### App mobile

```bash
cd mobile
npm install
# Su dispositivo/emulatore reale imposta l'IP della macchina che esegue il backend:
# EXPO_PUBLIC_API_URL=http://<ip-locale>:3000 npx expo start
npm start
```

L'app si aspetta il backend raggiungibile su `http://localhost:3000` per impostazione predefinita (valido per i simulatori iOS e per Expo web; su Android/dispositivo fisico va impostata la variabile `EXPO_PUBLIC_API_URL`).
