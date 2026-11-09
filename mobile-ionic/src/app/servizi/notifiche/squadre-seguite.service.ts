import { Injectable, computed, signal } from '@angular/core';

const CHIAVE = 'trovacampo.squadreSeguite';

/** Quante se ne possono seguire: lo stesso limite del backend (IscrizioniService). */
export const MASSIMO_SQUADRE_SEGUITE = 30;

/**
 * Una squadra seguita, con quanto serve a mostrarla nella pagina Notifiche
 * senza chiedere niente al server.
 *
 * @property chiave come la calcola il backend (ChiaveSquadra): società di
 *   presenze, campionato, ente e lettera della squadra. È l'unica cosa che
 *   arriva al server.
 * @property societaId la scheda in TrovaCampo, per tornarci dalla pagina Notifiche
 */
export interface SquadraSeguita {
  chiave: string;
  societa: string;
  societaId: string;
  campionato: string;
  dettaglio: string;
}

/**
 * Le squadre seguite, sul dispositivo: senza login non c'è un altro posto
 * dove tenerle. Al server vanno solo con l'iscrizione alle notifiche (vedi
 * NotificheService), quando l'avviso delle squadre è acceso.
 */
@Injectable({ providedIn: 'root' })
export class SquadreSeguiteService {
  readonly seguite = signal<SquadraSeguita[]>(ricordate());
  readonly chiavi = computed(() => this.seguite().map((squadra) => squadra.chiave));

  segue(chiave: string): boolean {
    return this.seguite().some((squadra) => squadra.chiave === chiave);
  }

  /** Falso se sono già troppe. */
  aggiungi(squadra: SquadraSeguita): boolean {
    if (this.segue(squadra.chiave)) {
      return true;
    }
    if (this.seguite().length >= MASSIMO_SQUADRE_SEGUITE) {
      return false;
    }
    this.salva([...this.seguite(), squadra]);
    return true;
  }

  togli(chiave: string): void {
    this.salva(this.seguite().filter((squadra) => squadra.chiave !== chiave));
  }

  private salva(seguite: SquadraSeguita[]): void {
    this.seguite.set(seguite);
    try {
      localStorage.setItem(CHIAVE, JSON.stringify(seguite));
    } catch {
      // Navigazione privata o archivio bloccato: valgono finché la pagina
      // resta aperta, e il server le ha comunque con l'iscrizione.
    }
  }
}

function ricordate(): SquadraSeguita[] {
  try {
    const lette: unknown = JSON.parse(localStorage.getItem(CHIAVE) ?? '[]');
    // Quello che c'è nell'archivio l'ha scritto una versione dell'app, forse
    // vecchia, forse toccata a mano: si tiene solo quello che ha la forma giusta.
    return Array.isArray(lette)
      ? lette.filter(
          (squadra): squadra is SquadraSeguita =>
            typeof squadra?.chiave === 'string' && typeof squadra?.societaId === 'string',
        )
      : [];
  } catch {
    return [];
  }
}
