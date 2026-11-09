/** Le preferenze delle notifiche sul dispositivo: tipi, valori predefiniti e archivio locale. */

const CHIAVE = 'trovacampo.notifiche';

/** I raggi fra cui scegliere per le partite vicine. */
export const RAGGI_KM = [5, 10, 20] as const;
export const RAGGIO_PREDEFINITO_KM = 10;

/** La posizione mandata al server, e quando: la pagina lo mostra. */
export interface PosizioneSalvata {
  lat: number;
  lng: number;
  /** ISO 8601. */
  il: string;
}

/** Le scelte di chi usa l'app, sul dispositivo. Entrambi gli avvisi partono spenti. */
export interface PreferenzeNotifiche {
  avvisoSquadre: boolean;
  avvisoVicino: boolean;
  raggioKm: number;
  posizione: PosizioneSalvata | null;
}

const PREDEFINITE: PreferenzeNotifiche = {
  avvisoSquadre: false,
  avvisoVicino: false,
  raggioKm: RAGGIO_PREDEFINITO_KM,
  posizione: null,
};

/** Tre decimali, un centinaio di metri: come IscrizioniService.arrotonda nel backend. */
export function arrotonda(gradi: number): number {
  return Math.round(gradi * 1000) / 1000;
}

export function preferenzeRicordate(): PreferenzeNotifiche {
  try {
    const lette = JSON.parse(
      localStorage.getItem(CHIAVE) ?? 'null',
    ) as Partial<PreferenzeNotifiche> | null;
    if (!lette || typeof lette !== 'object') {
      return PREDEFINITE;
    }
    const posizione = lette.posizione;
    return {
      avvisoSquadre: lette.avvisoSquadre === true,
      avvisoVicino: lette.avvisoVicino === true,
      raggioKm: (RAGGI_KM as readonly number[]).includes(lette.raggioKm ?? -1)
        ? lette.raggioKm!
        : RAGGIO_PREDEFINITO_KM,
      posizione:
        posizione && typeof posizione.lat === 'number' && typeof posizione.lng === 'number'
          ? posizione
          : null,
    };
  } catch {
    return PREDEFINITE;
  }
}

/** Scrive le preferenze sul dispositivo; con l'archivio bloccato valgono finché la pagina resta aperta. */
export function salvaPreferenze(preferenze: PreferenzeNotifiche): void {
  try {
    localStorage.setItem(CHIAVE, JSON.stringify(preferenze));
  } catch {
    // Archivio bloccato: le scelte valgono finché la pagina resta aperta.
  }
}
