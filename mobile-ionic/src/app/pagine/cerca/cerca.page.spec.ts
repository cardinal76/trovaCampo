import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { SocietaService } from '../../servizi/societa.service';
import { CercaPage } from './cerca.page';

describe('CercaPage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CercaPage],
      providers: [
        provideIonicAngular(),
        provideRouter([]),
        { provide: SocietaService, useValue: { tutti: () => of([]), cerca: () => of([]) } },
      ],
    });
  });

  /**
   * La prima pagina è quella su cui si arriva da footballer.it: il ritorno
   * deve stare qui, a vista, e non solo dentro il menu utente.
   */
  it('ha il collegamento per tornare a footballer.it, nella stessa scheda', () => {
    const fixture = TestBed.createComponent(CercaPage);
    fixture.detectChanges();

    const link: HTMLAnchorElement | null = fixture.nativeElement.querySelector('a.footballer');
    expect(link).not.toBeNull();
    expect(link!.getAttribute('href')).toBe('https://footballer.it');
    expect(link!.textContent?.trim()).toBe('Torna a footballer.it');
    // Si torna, non si apre accanto: niente target, quindi stessa scheda.
    expect(link!.hasAttribute('target')).toBeFalse();
  });
});
