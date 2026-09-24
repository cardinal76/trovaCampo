import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { ricordaAmministratore } from '../../servizi/amministratore-ricordato';
import { MenuUtenteComponent } from './menu-utente.component';

describe('MenuUtenteComponent', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [MenuUtenteComponent],
      providers: [provideIonicAngular(), provideRouter([])],
    });
  });

  afterEach(() => ricordaAmministratore(false));

  /**
   * Apre il menu e legge la voce di footballer.it: indirizzo e testo, o null
   * se non c'e'. Il contenuto del popover nasce solo all'apertura, quindi il
   * menu va aperto davvero.
   */
  async function vocePerFootballer(): Promise<{ href: string | null; testo: string } | null> {
    const fixture = TestBed.createComponent(MenuUtenteComponent);
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();

    const popover = fixture.nativeElement.querySelector('ion-popover') as HTMLIonPopoverElement;
    await popover.present();
    fixture.detectChanges();

    const voce = document.querySelector<HTMLElement>('ion-item.footballer');
    const letta = voce ? { href: (voce as HTMLIonItemElement).href ?? null, testo: voce.textContent?.trim() ?? '' } : null;
    await popover.dismiss();
    fixture.destroy();
    fixture.nativeElement.remove();
    return letta;
  }

  it('offre a chi non è entrato il ritorno a footballer.it', async () => {
    const voce = await vocePerFootballer();

    expect(voce).toEqual({ href: 'https://footballer.it', testo: 'Torna a footballer.it' });
  });

  it('lo offre anche a chi amministra, accanto a "Esci"', async () => {
    ricordaAmministratore(true, 'Marco');

    const voce = await vocePerFootballer();

    expect(voce?.href).toBe('https://footballer.it');
  });
});
