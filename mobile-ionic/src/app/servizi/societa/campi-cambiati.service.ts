import { Injectable, signal } from '@angular/core';

/**
 * Un contatore che sale ogni volta che chi amministra cambia l'archivio dei
 * campi (crea, modifica o elimina una scheda, importa un file o
 * l'anagrafica).
 *
 * Elenco, mappa e risultati scaricano i campi una volta sola, alla
 * creazione, e Ionic tiene le pagine in vita fra una navigazione e l'altra:
 * tornandoci dopo un'eliminazione mostrerebbero ancora la scheda che non
 * esiste più. Con questo contatore ricaricano al rientro solo se qualcosa è
 * cambiato davvero, invece di riscaricare centinaia di KB a ogni ritorno da
 * una scheda.
 *
 * Sta in un file a parte, senza keycloak-js, come amministratore-ricordato:
 * le pagine pubbliche lo leggono senza trascinarsi dietro il login.
 */
@Injectable({ providedIn: 'root' })
export class CampiCambiatiService {
  private readonly contatore = signal(0);

  /** Il valore da ricordare quando si scaricano i campi, e da confrontare al rientro. */
  readonly versione = this.contatore.asReadonly();

  segnala(): void {
    this.contatore.update((n) => n + 1);
  }
}
