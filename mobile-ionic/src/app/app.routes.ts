import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./pagine/cerca/cerca.page').then((m) => m.CercaPage),
  },
  {
    path: 'risultati',
    loadComponent: () => import('./pagine/risultati/risultati.page').then((m) => m.RisultatiPage),
  },
  {
    path: 'societa/:id',
    loadComponent: () => import('./pagine/scheda/scheda.page').then((m) => m.SchedaPage),
  },
  {
    path: 'campi',
    loadComponent: () => import('./pagine/elenco/elenco.page').then((m) => m.ElencoPage),
  },
  {
    path: 'mappa',
    loadComponent: () => import('./pagine/mappa/mappa.page').then((m) => m.MappaPage),
  },
  {
    // Gli avvisi push: squadre seguite e partite vicine.
    path: 'notifiche',
    loadComponent: () => import('./pagine/notifiche/notifiche.page').then((m) => m.NotifichePage),
  },
  {
    // Le partite per cui arriva un avviso: si apre da Notifiche.
    path: 'prossime-partite',
    loadComponent: () =>
      import('./pagine/prossime-partite/prossime-partite.page').then((m) => m.ProssimePartitePage),
  },
  {
    path: 'aggiungi',
    loadComponent: () => import('./pagine/aggiungi/aggiungi.page').then((m) => m.AggiungiPage),
  },
  {
    // «Hai dati da rettificare?»: pubblica, con ?societa=<id>&nome=<nome> dalla scheda.
    path: 'rettifica',
    loadComponent: () => import('./pagine/rettifica/rettifica.page').then((m) => m.RettificaPage),
  },
  {
    // Dalle voci "Accedi" ed "Esci" (con ?esci=1) del menu utente.
    path: 'accedi',
    loadComponent: () => import('./pagine/accesso/accesso.page').then((m) => m.AccessoPage),
  },
  {
    // Per chi amministra: non collegata dal resto dell'app, protetta dal token.
    path: 'admin/importazione',
    loadComponent: () =>
      import('./pagine/importazione/importazione.page').then((m) => m.ImportazionePage),
  },
  {
    // Le ASD/SSD del Registro nazionale: lista commerciale, solo per chi
    // amministra (login e ruolo li chiede la pagina, i dati il backend).
    path: 'admin/prospect',
    loadComponent: () => import('./pagine/prospect/prospect.page').then((m) => m.ProspectPage),
  },
  {
    // Le richieste di rettifica arrivate dal form pubblico: solo per chi amministra.
    path: 'admin/segnalazioni',
    loadComponent: () =>
      import('./pagine/segnalazioni/segnalazioni.page').then((m) => m.SegnalazioniPage),
  },
  {
    // Prima di :id, che altrimenti prenderebbe "nuova" per un identificativo.
    path: 'admin/societa/nuova',
    data: { nuova: true },
    loadComponent: () => import('./pagine/modifica/modifica.page').then((m) => m.ModificaPage),
  },
  {
    // Per chi amministra: si apre dal pulsante "Modifica" della scheda.
    path: 'admin/societa/:id',
    loadComponent: () => import('./pagine/modifica/modifica.page').then((m) => m.ModificaPage),
  },
  { path: '**', redirectTo: '' },
];
