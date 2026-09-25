import { leggiCoordinate, scriviCoordinate } from './coordinate';

describe('coordinate in un solo campo', () => {
  it('legge la coppia come la copia Google Maps', () => {
    expect(leggiCoordinate('41.507315, 13.058757')).toEqual({ lat: 41.507315, lng: 13.058757 });
    expect(leggiCoordinate('  41.507315,13.058757  ')).toEqual({ lat: 41.507315, lng: 13.058757 });
  });

  it('accetta spazi, punto e virgola e la virgola decimale all italiana', () => {
    expect(leggiCoordinate('41.507315 13.058757')).toEqual({ lat: 41.507315, lng: 13.058757 });
    expect(leggiCoordinate('41.507315; 13.058757')).toEqual({ lat: 41.507315, lng: 13.058757 });
    expect(leggiCoordinate('41,507315 13,058757')).toEqual({ lat: 41.507315, lng: 13.058757 });
    expect(leggiCoordinate('41,507315, 13,058757')).toEqual({ lat: 41.507315, lng: 13.058757 });
    expect(leggiCoordinate('-33.86, 151.2')).toEqual({ lat: -33.86, lng: 151.2 });
  });

  it('vuoto vuol dire da ricalcolare', () => {
    expect(leggiCoordinate('')).toBeUndefined();
    expect(leggiCoordinate('   ')).toBeUndefined();
  });

  it('non indovina quello che non si capisce o è fuori dal mondo', () => {
    expect(leggiCoordinate('41.507315')).toBeNull();
    expect(leggiCoordinate('Via Roma 1')).toBeNull();
    expect(leggiCoordinate('41.5, 13.0, 7')).toBeNull();
    expect(leggiCoordinate('141.5, 13.0')).toBeNull();
    expect(leggiCoordinate('41.5, 213.0')).toBeNull();
  });

  it('riscrive nello stesso formato che si incolla', () => {
    expect(scriviCoordinate(41.507315, 13.058757)).toBe('41.507315, 13.058757');
    expect(scriviCoordinate(undefined, 13)).toBe('');
    expect(leggiCoordinate(scriviCoordinate(41.9, 12.5))).toEqual({ lat: 41.9, lng: 12.5 });
  });
});
