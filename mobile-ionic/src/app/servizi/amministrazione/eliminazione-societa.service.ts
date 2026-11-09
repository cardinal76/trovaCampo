import { Injectable, Injector, inject, signal } from '@angular/core';
import { AlertController, NavController, ToastController } from '@ionic/angular/standalone';
import { Societa, daPresenze, nomeCompleto } from '../../modelli/societa';
import { archivioSessione } from '../archivio-locale';

/**
 * La scheda da eliminare quando si torna dal login di Keycloak. Il login
 * porta via dalla pagina, e al ritorno l'eliminazione chiesta prima non c'è
 * più: la si ricorda qui, per chiedere di nuovo la conferma appena rientrati.
 */
const CHIAVE_ELIMINA_DOPO_ACCESSO = 'trovacampo.eliminaDopoAccesso';

/**
 * L'eliminazione di una società da parte di chi amministra: conferma, login
 * se serve, cancellazione, avviso e ritorno all'elenco.
 */
@Injectable({ providedIn: 'root' })
export class EliminazioneSocietaService {
  private readonly avvisi = inject(ToastController);
  private readonly conferme = inject(AlertController);
  private readonly navigazione = inject(NavController);
  private readonly iniettore = inject(Injector);

  /** Mentre l'eliminazione è in corso il pulsante è spento: niente doppi tocchi. */
  readonly inCorso = signal(false);

  /**
   * Il pulsante cestino, per chi amministra: chiede conferma, elimina la
   * scheda e torna all'elenco. Una scheda che viene da presenze il backend
   * la mette fra le escluse, così la sincronizzazione non la ricrea: la
   * conferma lo dice, perché è la differenza con il solo "sparisce".
   */
  async elimina(societa: Societa): Promise<void> {
    if (this.inCorso()) {
      return;
    }
    if (!(await this.conferma(societa))) {
      return;
    }

    this.inCorso.set(true);
    try {
      const { amministrazione, autenticazione } = await this.serviziAmministrazione();
      if (!autenticazione.amministratore()) {
        // Keycloak non è ancora partito in questa pagina (si arriva dalla
        // ricerca, non dalla modifica): il login porta via e riporta qui.
        // Se non torna indietro (utente già entrato ma senza ruolo) si
        // prosegue e il server risponde 403 con il suo messaggio.
        scriviEliminaDopoAccesso(societa.id);
        await autenticazione.accedi();
        scriviEliminaDopoAccesso(null);
      }
      amministrazione.elimina(societa.id).subscribe({
        next: async () => {
          this.inCorso.set(false);
          await this.avvisaEsito('Società eliminata.', 'success');
          // All'elenco, ripartendo da capo: tornando indietro si finirebbe
          // sulla scheda, che non esiste più.
          await this.navigazione.navigateRoot('/campi');
        },
        error: async (errore: Error) => {
          this.inCorso.set(false);
          await this.avvisaEsito(errore.message, 'danger');
        },
      });
    } catch {
      scriviEliminaDopoAccesso(null);
      this.inCorso.set(false);
      await this.avvisaEsito('Il login non risponde. Riprova tra poco.', 'danger');
    }
  }

  /** Di ritorno dal login chiesto dal cestino: si finisce il login e si richiede la conferma. */
  async riprendi(societa: Societa): Promise<void> {
    try {
      const { autenticazione } = await this.serviziAmministrazione();
      await autenticazione.accedi();
    } catch {
      await this.avvisaEsito('Il login non risponde. Riprova tra poco.', 'danger');
      return;
    }
    await this.elimina(societa);
  }

  private async conferma(societa: Societa): Promise<boolean> {
    const avviso = await this.conferme.create({
      header: `Eliminare ${nomeCompleto(societa)} – ${societa.nomeImpianto}?`,
      message: daPresenze(societa)
        ? 'Sparirà da ricerca, elenco e mappa, e non ricomparirà con la sincronizzazione ' +
          "con presenze. L'esclusione si annulla da Importa campi."
        : 'Sparirà da ricerca, elenco e mappa. Non si può annullare.',
      buttons: [
        { text: 'Annulla', role: 'cancel' },
        { text: 'Elimina', role: 'destructive' },
      ],
    });
    await avviso.present();
    const { role } = await avviso.onDidDismiss();
    return role === 'destructive';
  }

  /**
   * Amministrazione e login caricati solo al primo tocco del cestino: la
   * scheda è una pagina pubblica, e chi cerca un campo non deve scaricare
   * keycloak-js (vedi AutenticazioneService).
   */
  private async serviziAmministrazione() {
    const [{ AmministrazioneService }, { AutenticazioneService }] = await Promise.all([
      import('../../servizi/amministrazione/amministrazione.service'),
      import('../../servizi/amministrazione/autenticazione.service'),
    ]);
    return {
      amministrazione: this.iniettore.get(AmministrazioneService),
      autenticazione: this.iniettore.get(AutenticazioneService),
    };
  }

  private async avvisaEsito(messaggio: string, colore: 'success' | 'danger'): Promise<void> {
    const avviso = await this.avvisi.create({
      message: messaggio,
      duration: colore === 'success' ? 2000 : 4000,
      color: colore,
      position: 'bottom',
    });
    await avviso.present();
  }
}

/** La scheda la cui eliminazione era in corso prima del login, se c'è. */
export function leggiEliminaDopoAccesso(): string | null {
  // Con l'archivio bloccato, dopo il login si tocca di nuovo il cestino.
  return archivioSessione.leggi(CHIAVE_ELIMINA_DOPO_ACCESSO);
}

export function scriviEliminaDopoAccesso(id: string | null): void {
  if (id) {
    archivioSessione.scrivi(CHIAVE_ELIMINA_DOPO_ACCESSO, id);
  } else {
    archivioSessione.togli(CHIAVE_ELIMINA_DOPO_ACCESSO);
  }
}
