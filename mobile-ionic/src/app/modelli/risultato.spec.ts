import {
  Risultato,
  dettaglioRisultato,
  giornoRisultato,
  nomeEsito,
  punteggio,
  risultatoPerEsteso,
  siglaEsito,
} from './risultato';

const VITTORIA: Risultato = {
  squadraIscrittaId: 50,
  data: '2026-10-11T10:30:00+02:00',
  campionato: 'UNDER 17 PROVINCIALE',
  ente: 'Roma',
  girone: 'A',
  giornata: 3,
  squadra: '',
  casa: 'A.S.D. ESEMPIO',
  ospite: 'ALTRA A.S.D.',
  golCasa: 2,
  golOspite: 1,
  inCasa: true,
  esito: 'VITTORIA',
};

describe('risultato', () => {
  it('scrive il giorno in italiano, con l ora di Roma', () => {
    expect(giornoRisultato(VITTORIA)).toBe('dom 11 ott');
  });

  /** Una partita giocata di cui presenze non ha ancora l'ora: il giorno manca. */
  it('senza data non inventa il giorno', () => {
    expect(giornoRisultato({ ...VITTORIA, data: null })).toBeNull();
    expect(giornoRisultato({ ...VITTORIA, data: undefined })).toBeNull();
  });

  it('scrive il punteggio sempre casa prima di ospite', () => {
    expect(punteggio(VITTORIA)).toBe('2 - 1');
    expect(punteggio({ ...VITTORIA, inCasa: false })).toBe('2 - 1');
  });

  it('il bollino e la lettera dell esito, il nome per intero per chi non vede', () => {
    expect(siglaEsito(VITTORIA)).toBe('V');
    expect(siglaEsito({ ...VITTORIA, esito: 'PAREGGIO' })).toBe('P');
    expect(siglaEsito({ ...VITTORIA, esito: 'SCONFITTA' })).toBe('S');
    expect(nomeEsito({ ...VITTORIA, esito: 'SCONFITTA' })).toBe('Sconfitta');
  });

  it('legge la riga per esteso, con l avversario e da che parte si giocava', () => {
    expect(risultatoPerEsteso(VITTORIA)).toBe(
      'Vittoria in casa 2 - 1 contro ALTRA A.S.D., dom 11 ott',
    );
    expect(
      risultatoPerEsteso({
        ...VITTORIA,
        inCasa: false,
        esito: 'PAREGGIO',
        golCasa: 1,
        golOspite: 1,
        data: null,
      }),
    ).toBe('Pareggio in trasferta 1 - 1 contro A.S.D. ESEMPIO');
  });

  it('nel dettaglio salta le parti che presenze non manda', () => {
    expect(dettaglioRisultato(VITTORIA)).toBe('3ª giornata · Girone A · Roma');
    expect(dettaglioRisultato({ ...VITTORIA, squadra: 'B' })).toBe(
      '3ª giornata · Girone A · Roma · squadra B',
    );
    expect(
      dettaglioRisultato({ ...VITTORIA, girone: null, ente: null, giornata: null }),
    ).toBe('');
  });
});
