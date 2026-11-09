import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';
import { AccessoSocieta, paginaAccesso, stemmaAccesso } from '../../presenze';

/**
 * «Accedi a Footballer» accanto alla scheda di una società che ne è cliente:
 * porta alla pagina di accesso di quel club su presenze (nome e stemma sopra
 * il modulo), con il suo stemma.
 *
 * <p>Il testo c'è sempre e dice il nome del club; l'immagine è decorazione
 * (`alt` vuoto: il nome è già scritto accanto). Se lo stemma manca, o non si
 * carica, resta il solo testo: nessuna immagine rotta e nessun blocco. Senza
 * un accesso utilizzabile, per esempio da un presenze che non manda ancora il
 * campo, il componente non disegna niente: nessun collegamento morto.
 *
 * <p>Si apre fuori dall'app (`target="_blank"`, `rel="noopener"`) come gli altri
 * collegamenti a presenze della scheda. Alto almeno 44px, con il focus visibile.
 */
@Component({
  selector: 'accesso-club',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (indirizzo(); as indirizzo) {
      <a class="accesso" [href]="indirizzo" target="_blank" rel="noopener">
        @if (immagine(); as immagine) {
          <img
            [src]="immagine"
            alt=""
            width="28"
            height="28"
            loading="lazy"
            decoding="async"
            referrerpolicy="no-referrer"
            (error)="nonCaricata.set(true)"
          />
        }
        <span class="testo">
          <b>Accedi a Footballer</b>
          <span>{{ nome() }}</span>
        </span>
      </a>
    }
  `,
  styles: `
    :host {
      display: block;
    }

    .accesso {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      min-height: 44px;
      max-width: 100%;
      box-sizing: border-box;
      padding: 0.375rem 0.75rem;
      border: 1px solid var(--ion-color-step-200, #ccc);
      border-radius: 10px;
      color: var(--ion-color-primary, #3880ff);
      text-decoration: none;
    }

    .accesso:focus-visible {
      outline: 2px solid var(--ion-color-primary, #3880ff);
      outline-offset: 2px;
    }

    img {
      flex: none;
      width: 28px;
      height: 28px;
      object-fit: contain;
      border-radius: 6px;
    }

    .testo {
      display: grid;
      min-width: 0;
      overflow-wrap: anywhere;
    }

    .testo span {
      font-size: 0.8125rem;
      color: var(--ion-color-medium, #666);
    }
  `,
})
export class AccessoClubComponent {
  /** Il nome del club, scritto nel collegamento. */
  readonly nome = input<string | null | undefined>('');
  /** Come si entra, da presenze; assente per chi non è cliente. */
  readonly accesso = input<AccessoSocieta | null | undefined>(null);
  /** Lo stemma dell'anagrafica, se il club non ne ha caricato uno suo. */
  readonly logoUrl = input<string | null | undefined>(undefined);

  /** L'immagine ha dato errore: si tiene il solo testo. */
  readonly nonCaricata = signal(false);

  readonly indirizzo = computed(() => paginaAccesso(this.accesso()));

  readonly immagine = computed(() => {
    if (this.nonCaricata()) {
      return null;
    }
    const logo = this.logoUrl();
    return stemmaAccesso(this.accesso()) ?? (logo?.startsWith('https://') ? logo : null);
  });

  constructor() {
    // Un'altra scheda con lo stesso componente: l'errore di prima non vale.
    effect(() => {
      this.accesso();
      this.logoUrl();
      this.nonCaricata.set(false);
    });
  }
}
