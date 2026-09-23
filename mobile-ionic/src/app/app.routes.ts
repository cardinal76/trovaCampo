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
    path: 'aggiungi',
    loadComponent: () => import('./pagine/aggiungi/aggiungi.page').then((m) => m.AggiungiPage),
  },
  {
    // Per chi amministra: non collegata dal resto dell'app, protetta dal token.
    path: 'admin/importazione',
    loadComponent: () =>
      import('./pagine/importazione/importazione.page').then((m) => m.ImportazionePage),
  },
  { path: '**', redirectTo: '' },
];
