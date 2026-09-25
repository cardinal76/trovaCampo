import { linkPercorso, partenzaDallaPosizione, suggerimentoPartenza } from './percorso';

const CAMPO = { lat: 41.9352, lng: 12.4561 };
const QUI = { lat: 41.892, lng: 12.482 };

describe('linkPercorso', () => {
  it('senza partenza lascia fuori origin: Google parte dalla posizione del dispositivo', () => {
    expect(linkPercorso(CAMPO, '', null)).toBe(
      'https://www.google.com/maps/dir/?api=1&destination=41.9352,12.4561',
    );
  });

  it('con la posizione nota parte da lì, come "lat,lng"', () => {
    expect(linkPercorso(CAMPO, '', QUI)).toBe(
      'https://www.google.com/maps/dir/?api=1&origin=41.892,12.482&destination=41.9352,12.4561',
    );
  });

  it("l'indirizzo scritto vince sulla posizione, codificato per l'URL", () => {
    expect(linkPercorso(CAMPO, ' Via dei Mille 3/A, Roma & dintorni ', QUI)).toBe(
      'https://www.google.com/maps/dir/?api=1' +
        '&origin=Via%20dei%20Mille%203%2FA%2C%20Roma%20%26%20dintorni' +
        '&destination=41.9352,12.4561',
    );
  });

  it('un indirizzo di soli spazi conta come vuoto', () => {
    expect(linkPercorso(CAMPO, '   ', QUI)).toContain('&origin=41.892,12.482&');
    expect(linkPercorso(CAMPO, undefined, null)).not.toContain('origin');
  });

  it('accorcia le coordinate a sei decimali, senza notazione esponenziale', () => {
    expect(linkPercorso({ lat: 41.123456789, lng: 0.0000001 }, '', { lat: -0.1234564, lng: 9 })).toBe(
      'https://www.google.com/maps/dir/?api=1&origin=-0.123456,9&destination=41.123457,0',
    );
  });
});

describe('partenza predefinita', () => {
  it('è la posizione se il browser la può dare, anche dopo averla sbloccata', () => {
    for (const caso of ['consentita', 'da-consentire', 'sconosciuta', 'bloccata', 'rifiutata'] as const) {
      expect(partenzaDallaPosizione(caso)).withContext(caso).toBeTrue();
      expect(suggerimentoPartenza(caso)).toBe('La mia posizione');
    }
  });

  it('è da scrivere se la posizione qui non arriva mai', () => {
    for (const caso of ['non-sicura', 'non-supportata', 'vietata-dal-sito'] as const) {
      expect(partenzaDallaPosizione(caso)).withContext(caso).toBeFalse();
      expect(suggerimentoPartenza(caso)).toBe('Scrivi da dove parti');
    }
  });
});
