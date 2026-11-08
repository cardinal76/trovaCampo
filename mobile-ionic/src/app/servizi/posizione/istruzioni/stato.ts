import { CasoNotifiche, CasoPosizione } from '../diagnosi.service';

/** Le due parole della riga di stato in cima alla pagina Notifiche. */
export function statoNotifiche(caso: CasoNotifiche): { testo: string; tono: Tono } {
  switch (caso) {
    case 'consentite':
      return { testo: 'consentite', tono: 'bene' };
    case 'da-consentire':
      return { testo: 'da consentire', tono: 'neutro' };
    case 'bloccate':
      return { testo: 'bloccate', tono: 'male' };
    default:
      return { testo: 'non disponibili qui', tono: 'attenzione' };
  }
}

/** null quando le API non lo dicono e non la si è ancora chiesta: meglio tacere che tirare a indovinare. */
export function statoPosizione(caso: CasoPosizione): { testo: string; tono: Tono } | null {
  switch (caso) {
    case 'consentita':
      return { testo: 'consentita', tono: 'bene' };
    case 'da-consentire':
      return { testo: 'da consentire', tono: 'neutro' };
    case 'rifiutata':
      return { testo: 'non consentita', tono: 'attenzione' };
    case 'bloccata':
      return { testo: 'bloccata', tono: 'male' };
    case 'vietata-dal-sito':
      return { testo: 'disattivata dal sito', tono: 'male' };
    case 'non-disponibile':
    case 'scaduta':
      return { testo: 'non trovata', tono: 'attenzione' };
    case 'non-supportata':
    case 'non-sicura':
      return { testo: 'non disponibile qui', tono: 'attenzione' };
    case 'sconosciuta':
      return null;
  }
}

export type Tono = 'bene' | 'neutro' | 'attenzione' | 'male';
