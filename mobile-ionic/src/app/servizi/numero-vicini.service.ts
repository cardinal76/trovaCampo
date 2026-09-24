import { Injectable, signal } from '@angular/core';
import { numeroViciniValido } from '../modelli/vicini';

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
    try {
      localStorage.setItem(CHIAVE, String(valido));
    } catch {
      // Navigazione privata o archivio bloccato: vale finché la pagina resta aperta.
    }
  }
}

function ricordato(): number {
  try {
    return numeroViciniValido(localStorage.getItem(CHIAVE));
  } catch {
    return numeroViciniValido(null);
  }
}
