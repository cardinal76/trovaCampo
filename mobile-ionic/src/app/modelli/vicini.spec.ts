import { SocietaGeolocalizzata } from './societa';
import {
  NUMERO_VICINI_PREDEFINITO,
  distanzaKm,
  numeroViciniValido,
  piuVicini,
  testoDistanza,
} from './vicini';

function campo(id: string, lat: number, lng: number): SocietaGeolocalizzata {
  return {
    id,
    siglaSocieta: '',
    nomeSocieta: id,
    comitatoRegionale: '',
    nomeImpianto: '',
    indirizzoImpianto: '',
    localitaImpianto: '',
    provinciaImpianto: 'RM',
    lat,
    lng,
  };
}

const COLOSSEO = { lat: 41.8902, lng: 12.4922 };

describe('distanzaKm', () => {
  it('è zero sullo stesso punto', () => {
    expect(distanzaKm(COLOSSEO, COLOSSEO)).toBe(0);
  });

  it('Roma-Latina sono circa 57 km in linea d aria, in un verso e nell altro', () => {
    const latina = { lat: 41.4676, lng: 12.9037 };
    expect(distanzaKm(COLOSSEO, latina)).toBeCloseTo(58, 0);
    expect(distanzaKm(latina, COLOSSEO)).toBeCloseTo(distanzaKm(COLOSSEO, latina), 9);
  });

  it('un grado di latitudine è circa 111 km', () => {
    expect(distanzaKm({ lat: 41, lng: 12 }, { lat: 42, lng: 12 })).toBeCloseTo(111.2, 1);
  });
});

describe('piuVicini', () => {
  const campi = [
    campo('lontano', 42.4, 12.1),
    campo('vicino', 41.891, 12.493),
    campo('medio', 41.95, 12.5),
    campo('stesso-impianto', 41.891, 12.493),
  ];

  it('ordina dal più vicino e tiene solo i primi n', () => {
    expect(piuVicini(campi, COLOSSEO, 3).map((v) => v.campo.id)).toEqual([
      'vicino',
      'stesso-impianto',
      'medio',
    ]);
  });

  it('dà anche la distanza di ciascuno, crescente', () => {
    const distanze = piuVicini(campi, COLOSSEO, 4).map((v) => v.distanzaKm);
    expect(distanze).toEqual([...distanze].sort((a, b) => a - b));
    expect(distanze[0]).toBeLessThan(0.2);
  });

  it('con n più grande dei campi li dà tutti, con n = 0 nessuno', () => {
    expect(piuVicini(campi, COLOSSEO, 10).length).toBe(4);
    expect(piuVicini(campi, COLOSSEO, 0)).toEqual([]);
    expect(piuVicini([], COLOSSEO, 3)).toEqual([]);
  });

  it('non cambia l elenco che riceve', () => {
    const copia = [...campi];
    piuVicini(campi, COLOSSEO, 2);
    expect(campi).toEqual(copia);
  });
});

describe('testoDistanza', () => {
  it('in metri sotto il chilometro, arrotondati alla decina', () => {
    expect(testoDistanza(0.348)).toBe('350 m');
    expect(testoDistanza(0.001)).toBe('10 m');
  });

  it('in chilometri con la virgola, senza decimali da 10 km', () => {
    expect(testoDistanza(1.234)).toBe('1,2 km');
    expect(testoDistanza(9.96)).toBe('10,0 km');
    expect(testoDistanza(57.6)).toBe('58 km');
  });
});

describe('numeroViciniValido', () => {
  it('accetta le scelte proposte, anche scritte come testo', () => {
    expect(numeroViciniValido(5)).toBe(5);
    expect(numeroViciniValido('10')).toBe(10);
  });

  it('qualunque altra cosa torna al predefinito', () => {
    for (const valore of [null, undefined, '', 'tre', 0, -1, 7, 1000]) {
      expect(numeroViciniValido(valore)).toBe(NUMERO_VICINI_PREDEFINITO);
    }
  });
});
