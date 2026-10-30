/**
 * Una ASD/SSD del Registro nazionale (RASD) con il suo campo, come la manda
 * GET /api/admin/prospect: solo quelle con le coordinate, solo per chi
 * amministra. Nessun dato di persone fisiche.
 */
export interface Prospect {
  codiceFiscale: string;
  denominazione: string;
  regione?: string;
  comune?: string;
  organismi?: string[];
  discipline?: string[];
  tesseratiAttivi?: number;
  calcio: boolean;
  nomeImpianto?: string;
  indirizzo?: string;
  comuneCampo?: string;
  lat: number;
  lng: number;
  fonteIndirizzo?: FonteIndirizzo;
}

export type FonteIndirizzo = 'CAMPO_TROVACAMPO' | 'CAMPO_PRESENZE' | 'OPEN_DATA' | 'GEOCODIFICA';

export const TESTO_FONTE: Record<FonteIndirizzo, string> = {
  CAMPO_TROVACAMPO: 'campo di TrovaCampo',
  CAMPO_PRESENZE: 'campo da presenze (comunicati LND)',
  OPEN_DATA: 'censimento impianti (open data)',
  GEOCODIFICA: 'geocodifica (indicativa)',
};

/** I filtri della mappa: stringa vuota vuol dire «tutti». */
export interface FiltriProspect {
  regione: string;
  disciplina: string;
  organismo: string;
}

export const NESSUN_FILTRO: FiltriProspect = { regione: '', disciplina: '', organismo: '' };

/**
 * Le scelte di ogni filtro, prese dai prospect stessi e in ordine
 * alfabetico: un'opzione che non dà risultati non compare.
 */
export function opzioniFiltri(prospect: Prospect[]): {
  regioni: string[];
  discipline: string[];
  organismi: string[];
} {
  const regioni = new Set<string>();
  const discipline = new Set<string>();
  const organismi = new Set<string>();
  for (const p of prospect) {
    if (p.regione) {
      regioni.add(p.regione);
    }
    (p.discipline ?? []).forEach((d) => discipline.add(d));
    (p.organismi ?? []).forEach((o) => organismi.add(o));
  }
  const ordina = (valori: Set<string>) => [...valori].sort((a, b) => a.localeCompare(b, 'it'));
  return { regioni: ordina(regioni), discipline: ordina(discipline), organismi: ordina(organismi) };
}

/** I prospect che passano tutti e tre i filtri. */
export function filtraProspect(prospect: Prospect[], filtri: FiltriProspect): Prospect[] {
  return prospect.filter(
    (p) =>
      (!filtri.regione || p.regione === filtri.regione) &&
      (!filtri.disciplina || (p.discipline ?? []).includes(filtri.disciplina)) &&
      (!filtri.organismo || (p.organismi ?? []).includes(filtri.organismo)),
  );
}
