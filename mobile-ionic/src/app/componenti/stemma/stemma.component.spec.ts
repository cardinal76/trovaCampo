import { ComponentFixture, TestBed } from '@angular/core/testing';

import { inizialeDi, StemmaComponent } from './stemma.component';

describe('inizialeDi', () => {
  it('salta le sigle della forma giuridica', () => {
    expect(inizialeDi('A.S.D. PALOCCO')).toBe('P');
    expect(inizialeDi('ASD Accademia Gialloazzurri')).toBe('A');
    expect(inizialeDi('S.S.D. LODIGIANI')).toBe('L');
    expect(inizialeDi('POL. TOR SAPIENZA')).toBe('T');
    expect(inizialeDi('a.p.d. palocco')).toBe('P');
  });

  it('tiene i nomi che cominciano per una lettera sola o una cifra', () => {
    expect(inizialeDi('A FERRARIS VILLANOVA 1956')).toBe('A');
    expect(inizialeDi('1970 CALCIO')).toBe('1');
    expect(inizialeDi('LODIGIANI CALCIO 1972')).toBe('L');
  });

  it('non resta mai vuota', () => {
    expect(inizialeDi('A.S.D.')).toBe('A');
    expect(inizialeDi('')).toBe('?');
    expect(inizialeDi(undefined)).toBe('?');
  });
});

describe('StemmaComponent', () => {
  let fixture: ComponentFixture<StemmaComponent>;

  function crea(nome: string, logoUrl?: string, dimensione = 48) {
    fixture = TestBed.createComponent(StemmaComponent);
    fixture.componentRef.setInput('nome', nome);
    fixture.componentRef.setInput('logoUrl', logoUrl);
    fixture.componentRef.setInput('dimensione', dimensione);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it("mostra lo stemma, senza alt e senza referrer, caricato quando serve", () => {
    const elemento = crea('A.S.D. PALOCCO', 'https://play.lnd.it/lndimg/1/1-web.png', 36);
    const img = elemento.querySelector('img')!;

    expect(img.getAttribute('src')).toBe('https://play.lnd.it/lndimg/1/1-web.png');
    expect(img.getAttribute('alt')).toBe('');
    expect(img.getAttribute('loading')).toBe('lazy');
    expect(img.getAttribute('referrerpolicy')).toBe('no-referrer');
    expect(elemento.querySelector('.segnaposto')).toBeNull();
    expect(elemento.style.getPropertyValue('--stemma-dimensione')).toBe('36px');
  });

  it("senza stemma mostra l'iniziale che salta la sigla", () => {
    const elemento = crea('A.S.D. PALOCCO');

    expect(elemento.querySelector('img')).toBeNull();
    expect(elemento.querySelector('.segnaposto')!.textContent!.trim()).toBe('P');
  });

  it('un indirizzo che non è https non si carica', () => {
    const elemento = crea('BOREALE', 'http://play.lnd.it/lndimg/1/1-web.png');

    expect(elemento.querySelector('img')).toBeNull();
    expect(elemento.querySelector('.segnaposto')!.textContent!.trim()).toBe('B');
  });

  it("se l'immagine non si carica torna al segnaposto, e riprova con uno stemma nuovo", () => {
    const elemento = crea('BOREALE', 'https://play.lnd.it/lndimg/1/1-web.png');

    elemento.querySelector('img')!.dispatchEvent(new Event('error'));
    fixture.detectChanges();
    expect(elemento.querySelector('img')).toBeNull();
    expect(elemento.querySelector('.segnaposto')!.textContent!.trim()).toBe('B');

    fixture.componentRef.setInput('logoUrl', 'https://play.lnd.it/lndimg/2/2-web.png');
    fixture.detectChanges();
    expect(elemento.querySelector('img')!.getAttribute('src')).toBe(
      'https://play.lnd.it/lndimg/2/2-web.png',
    );
  });
});
