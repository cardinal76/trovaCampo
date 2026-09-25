import { stemmaValido } from './modifica.page';

describe('stemmaValido', () => {
  it('accetta un indirizzo https', () => {
    expect(stemmaValido('https://play.lnd.it/lndimg/1/1-web.png')).toBeTrue();
  });

  it('rifiuta http, testo e spazi dentro, come il server', () => {
    expect(stemmaValido('http://esempio.it/s.png')).toBeFalse();
    expect(stemmaValido('stemma.png')).toBeFalse();
    expect(stemmaValido('https://esempio.it/uno stemma.png')).toBeFalse();
    expect(stemmaValido('')).toBeFalse();
  });
});
