import { amministratoreRicordato, nomeRicordato, ricordaAmministratore } from './amministratore-ricordato';

describe('amministratoreRicordato', () => {
  afterEach(() => ricordaAmministratore(false));

  it('di default non mostra il pulsante', () => {
    expect(amministratoreRicordato()).toBeFalse();
  });

  it('ricorda un amministratore e lo dimentica all\'uscita', () => {
    ricordaAmministratore(true);
    expect(amministratoreRicordato()).toBeTrue();

    ricordaAmministratore(false);
    expect(amministratoreRicordato()).toBeFalse();
  });

  it('ricorda il nome solo finché l\'amministratore resta entrato', () => {
    ricordaAmministratore(true, 'Marco');
    expect(nomeRicordato()).toBe('Marco');

    ricordaAmministratore(false);
    expect(nomeRicordato()).toBeNull();
  });

  it('senza localStorage non mostra il pulsante e non si rompe', () => {
    spyOn(Storage.prototype, 'getItem').and.throwError('bloccato');
    spyOn(Storage.prototype, 'setItem').and.throwError('bloccato');

    expect(() => ricordaAmministratore(true)).not.toThrow();
    expect(amministratoreRicordato()).toBeFalse();
  });
});
