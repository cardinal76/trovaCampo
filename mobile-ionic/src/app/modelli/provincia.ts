import { Societa } from './societa';

/** Nessun filtro: tutti i campi. */
export const TUTTE = 'tutte';

/** I campi di cui il backend non ha potuto ricavare la provincia dal comune. */
export const SCONOSCIUTA = 'sconosciuta';

/** Le sigle che arrivano dai dati, col nome da mostrare nel filtro. */
const NOMI: Record<string, string> = {
  RM: 'Roma',
  LT: 'Latina',
  FR: 'Frosinone',
  RI: 'Rieti',
  VT: 'Viterbo',
};

export interface OpzioneProvincia {
  valore: string;
  etichetta: string;
}

/** La sigla del campo, "" se manca. Maiuscola: i file a mano la scrivono come capita. */
function siglaDi(campo: Societa): string {
  return (campo.provinciaImpianto ?? '').trim().toUpperCase();
}

/** "Roma" per RM; una sigla fuori dal Lazio resta la sigla. */
export function nomeProvincia(sigla: string): string {
  return NOMI[sigla] ?? sigla;
}

/**
 * Le scelte del filtro: "Tutte", poi le province che compaiono davvero nei
 * dati in ordine di nome, e "Provincia sconosciuta" solo se c'è qualche
 * campo senza. Così il filtro non offre mai una provincia che darebbe un
 * elenco vuoto.
 */
export function opzioniProvincia(campi: Societa[]): OpzioneProvincia[] {
  const sigle = new Set(campi.map(siglaDi));
  const province = [...sigle]
    .filter(Boolean)
    .map((sigla) => ({ valore: sigla, etichetta: nomeProvincia(sigla) }))
    .sort((a, b) => a.etichetta.localeCompare(b.etichetta, 'it'));
  return [
    { valore: TUTTE, etichetta: 'Tutte' },
    ...province,
    ...(sigle.has('') ? [{ valore: SCONOSCIUTA, etichetta: 'Provincia sconosciuta' }] : []),
  ];
}

/**
 * La scelta ricordata, se fra le opzioni c'è ancora; altrimenti "Tutte".
 * Una provincia scelta in una visita precedente può non avere più campi, e
 * nasconderli tutti senza che il filtro la mostri sarebbe un mistero.
 */
export function provinciaValida(scelta: string, opzioni: OpzioneProvincia[]): string {
  return opzioni.some((opzione) => opzione.valore === scelta) ? scelta : TUTTE;
}

export function nellaProvincia(campo: Societa, provincia: string): boolean {
  if (provincia === TUTTE) {
    return true;
  }
  return siglaDi(campo) === (provincia === SCONOSCIUTA ? '' : provincia);
}
