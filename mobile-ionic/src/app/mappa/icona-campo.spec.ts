import { SocietaGeolocalizzata } from '../modelli/societa';
import {
  ZOOM_ICONE,
  ZOOM_STEMMI,
  iconaPerTipo,
  iconaStemma,
  stemmaDi,
  tipoSegnaposto,
} from './icona-campo';

const STEMMA = 'https://play.lnd.it/lndimg/1/1-web.png';

function campo(valori: Partial<SocietaGeolocalizzata> = {}): SocietaGeolocalizzata {
  return {
    id: 'c1',
    siglaSocieta: 'A.S.D.',
    nomeSocieta: 'PALOCCO',
    comitatoRegionale: '',
    nomeImpianto: 'Campo Palocco',
    indirizzoImpianto: 'Via Roma 1',
    localitaImpianto: '',
    provinciaImpianto: 'RM',
    lat: 41.73,
    lng: 12.36,
    ...valori,
  };
}

describe('icona del campo sulla mappa', () => {
  it('lo stemma non compare prima delle icone HTML: sul canvas non ci va', () => {
    expect(ZOOM_STEMMI).toBeGreaterThanOrEqual(ZOOM_ICONE);
  });

  describe('tipoSegnaposto', () => {
    it('da lontano è sempre il canvas, con o senza stemma', () => {
      expect(tipoSegnaposto(campo({ logoUrl: STEMMA }), ZOOM_ICONE - 1)).toBe('canvas');
      expect(tipoSegnaposto(campo(), 5)).toBe('canvas');
    });

    it('da vicino lo stemma, se la società ce l ha', () => {
      expect(tipoSegnaposto(campo({ logoUrl: STEMMA }), ZOOM_STEMMI)).toBe('stemma');
      expect(tipoSegnaposto(campo({ logoUrl: STEMMA }), 19)).toBe('stemma');
    });

    it('da vicino senza stemma resta l icona del campo', () => {
      expect(tipoSegnaposto(campo(), ZOOM_STEMMI)).toBe('campo');
      expect(tipoSegnaposto(campo({ logoUrl: '' }), 18)).toBe('campo');
      // Solo https, come nell'elenco: un http darebbe contenuto misto.
      expect(tipoSegnaposto(campo({ logoUrl: 'http://play.lnd.it/x.png' }), 18)).toBe('campo');
    });

    it('uno stemma che non si è caricato torna icona del campo', () => {
      const falliti = new Set([STEMMA]);
      expect(tipoSegnaposto(campo({ logoUrl: STEMMA }), 18, falliti)).toBe('campo');
      expect(tipoSegnaposto(campo({ logoUrl: STEMMA }), 10, falliti)).toBe('canvas');
    });
  });

  it('stemmaDi accetta solo indirizzi https', () => {
    expect(stemmaDi(campo({ logoUrl: ` ${STEMMA} ` }))).toBe(STEMMA);
    expect(stemmaDi(campo({ logoUrl: 'javascript:alert(1)' }))).toBeNull();
    expect(stemmaDi(campo())).toBeNull();
  });

  describe('iconaStemma', () => {
    function html(valori: Partial<SocietaGeolocalizzata>): HTMLElement {
      const contenitore = document.createElement('div');
      contenitore.innerHTML = iconaStemma(campo(valori)).options.html as string;
      return contenitore;
    }

    it('un tondo con lo stemma, ancorato alla punta sotto, con l id del campo', () => {
      const icona = iconaStemma(campo({ logoUrl: STEMMA }));
      const immagine = html({ logoUrl: STEMMA }).querySelector('img')!;

      expect(icona.options.className).toContain('icona-stemma');
      expect(immagine.getAttribute('src')).toBe(STEMMA);
      expect(immagine.dataset['stemma']).toBe('c1');
      expect(immagine.getAttribute('referrerpolicy')).toBe('no-referrer');
      // L'ancora è al centro in basso: la punta tocca il punto del campo.
      const [larghezza, altezza] = icona.options.iconSize as [number, number];
      expect(icona.options.iconAnchor).toEqual([larghezza / 2, altezza]);
      // Area toccabile almeno come l'icona del campo (35 × 25).
      expect(larghezza).toBeGreaterThanOrEqual(35);
      expect(altezza).toBeGreaterThanOrEqual(25);
    });

    it('i dati che arrivano da fuori non diventano HTML', () => {
      const contenitore = html({
        logoUrl: 'https://x/"><script>alert(1)</script>',
        nomeSocieta: '<b>X</b>',
      });

      expect(contenitore.querySelector('script')).toBeNull();
      expect(contenitore.querySelector('b')).toBeNull();
      expect(contenitore.querySelector('.nome-campo')!.textContent).toContain('<b>X</b>');
    });
  });

  it('iconaPerTipo sceglie fra stemma e campo', () => {
    const conStemma = campo({ logoUrl: STEMMA });
    expect(iconaPerTipo(conStemma, 'stemma').options.className).toContain('icona-stemma');
    expect(iconaPerTipo(conStemma, 'campo').options.className).toBe('icona-campo');
  });
});
