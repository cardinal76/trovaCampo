import { computed, inject, signal } from '@angular/core';
import { Posizione } from '../modelli/vicini';
import { DiagnosiService } from './diagnosi.service';
import { istruzioniPosizione } from './istruzioni';
import { ErrorePosizione, PosizioneService } from './posizione.service';

/**
 * Una richiesta della posizione fatta da chi guarda, con quello che serve
 * per mostrarla: se è in corso, il messaggio d'errore e il riquadro "come
 * sbloccarla". La usano "Vicino a me" della Mappa e il percorso fino a un
 * campo, così i blocchi si spiegano con le stesse parole ovunque.
 *
 * Una per ogni punto della pagina che chiede la posizione: l'errore del
 * percorso non deve comparire sotto "Vicino a me", né viceversa.
 *
 * Va creata in un contesto di iniezione (un campo del componente).
 */
export class RichiestaPosizione {
  private readonly servizio = inject(PosizioneService);
  private readonly diagnosi = inject(DiagnosiService);

  readonly inCorso = signal(false);
  readonly errore = signal<string | null>(null);
  /**
   * Dopo un errore, cosa blocca la posizione e come sbloccarla: le stesse
   * istruzioni della pagina Notifiche. Null se la diagnosi non sa dire di
   * più del messaggio d'errore, che allora resta da solo.
   */
  readonly riquadro = computed(() =>
    this.errore() === null
      ? null
      : istruzioniPosizione(this.diagnosi.posizione(), this.diagnosi.piattaforma),
  );

  /**
   * Chiede la posizione: la restituisce, o null se non è arrivata (e allora
   * {@link errore} dice perché). Mentre una richiesta è in corso le altre
   * non partono: sul telefono un doppio tocco non apre due permessi.
   */
  async chiedi(): Promise<Posizione | null> {
    if (this.inCorso()) {
      return null;
    }
    this.inCorso.set(true);
    this.errore.set(null);
    try {
      const posizione = await this.servizio.attuale();
      await this.diagnosi.esitoPosizione(null);
      return posizione;
    } catch (errore) {
      // Prima la diagnosi, poi il messaggio: così il riquadro compare già
      // con il caso giusto (bloccata o solo rifiutata), senza cambiare sotto gli occhi.
      await this.diagnosi.esitoPosizione(errore);
      this.errore.set(
        errore instanceof ErrorePosizione
          ? errore.message
          : 'Non è stato possibile avere la posizione. Riprova.',
      );
      return null;
    } finally {
      this.inCorso.set(false);
    }
  }
}
