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

Il progetto è attualmente in fase di **analisi dei requisiti e progettazione**: non contiene ancora codice sorgente, ma documenti di analisi, mockup grafici e specifiche funzionali che guideranno lo sviluppo dell'app.
