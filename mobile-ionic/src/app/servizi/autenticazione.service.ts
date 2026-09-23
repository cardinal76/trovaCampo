import { Injectable, signal } from '@angular/core';
import Keycloak from 'keycloak-js';
import { environment } from '../../environments/environment';
import { ricordaAmministratore } from './amministratore-ricordato';

/** Ruolo di realm, nel Keycloak di presenze, che apre l'importazione. */
export const RUOLO_AMMINISTRATORE = 'trovacampo-admin';

/**
 * Login con il Keycloak di presenze, solo per l'area di amministrazione.
 *
 * L'app resta pubblica: nessuna pagina di ricerca importa questo servizio,
 * quindi keycloak-js finisce nel pezzo di bundle della pagina di importazione
 * e chi cerca un campo non lo scarica nemmeno. Keycloak si inizializza la
 * prima volta che qualcuno chiama {@link accedi}, non all'avvio dell'app.
 */
@Injectable({ providedIn: 'root' })
export class AutenticazioneService {
  private keycloak: Keycloak | null = null;
  private avvio: Promise<boolean> | null = null;

  readonly nome = signal<string | null>(null);
  readonly amministratore = signal(false);

  /**
   * Manda al login di Keycloak se non si è già entrati, e torna qui dopo.
   * Si può chiamare più volte: `init()` di keycloak-js no, quindi la prima
   * promessa viene riusata.
   */
  accedi(): Promise<boolean> {
    this.avvio ??= this.inizializza().catch((errore) => {
      // Un Keycloak irraggiungibile non deve bloccare per sempre: al
      // prossimo tentativo si riprova da capo.
      this.avvio = null;
      throw errore;
    });
    return this.avvio;
  }

  /** Un token valido per almeno altri 30 secondi, rinnovato se serve. */
  async token(): Promise<string> {
    await this.accedi();
    const keycloak = this.keycloak!;
    await keycloak.updateToken(30);
    this.aggiornaUtente(keycloak);
    return keycloak.token!;
  }

  async esci(): Promise<void> {
    // Prima del logout, che porta via dalla pagina: il pulsante in home deve
    // sparire con l'uscita.
    ricordaAmministratore(false);
    await this.keycloak?.logout({ redirectUri: `${window.location.origin}/` });
  }

  private async inizializza(): Promise<boolean> {
    const keycloak = new Keycloak(environment.keycloak);
    this.keycloak = keycloak;

    const autenticato = await keycloak.init({
      onLoad: 'login-required',
      // Client pubblico: niente segreto da custodire, PKCE rende inutile un
      // codice intercettato. Stesse opzioni del frontend di presenze.
      pkceMethod: 'S256',
      // L'iframe di controllo vuole cookie di terze parti, che i browser
      // bloccano: il rinnovo lo fa updateToken() prima di ogni chiamata.
      checkLoginIframe: false,
    });
    this.aggiornaUtente(keycloak);
    return autenticato;
  }

  private aggiornaUtente(keycloak: Keycloak): void {
    const profilo = keycloak.tokenParsed as { preferred_username?: string; name?: string } | undefined;
    this.nome.set(profilo?.name ?? profilo?.preferred_username ?? null);
    const amministratore = keycloak.hasRealmRole(RUOLO_AMMINISTRATORE);
    this.amministratore.set(amministratore);
    ricordaAmministratore(amministratore);
  }
}
