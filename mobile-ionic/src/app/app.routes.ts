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
    path: 'aggiungi',
    loadComponent: () => import('./pagine/aggiungi/aggiungi.page').then((m) => m.AggiungiPage),
  },
  {
    // Per chi amministra: non collegata dal resto dell'app, protetta dal token.
    path: 'admin/importazione',
    loadComponent: () =>
      import('./pagine/importazione/importazione.page').then((m) => m.ImportazionePage),
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
