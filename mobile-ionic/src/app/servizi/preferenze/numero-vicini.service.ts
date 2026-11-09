import { Injectable, signal } from '@angular/core';
import { numeroViciniValido } from '../../modelli/vicini';
import { archivioLocale } from '../archivio-locale';

const CHIAVE = 'trovacampo.numeroVicini';

/**
 * Quanti campi mostrare con "Vicino a me". Resta fra una visita e l'altra,
 * come la provincia: chi ne vuole cinque li vuole anche la volta dopo.
 */
@Injectable({ providedIn: 'root' })
export class NumeroViciniService {
  readonly numero = signal(ricordato());

  scegli(numero: number): void {
    const valido = numeroViciniValido(numero);
    this.numero.set(valido);
    // Con l'archivio bloccato la scelta vale finché la pagina resta aperta.
    archivioLocale.scrivi(CHIAVE, String(valido));
  }
}

function ricordato(): number {
  return numeroViciniValido(archivioLocale.leggi(CHIAVE));
}
