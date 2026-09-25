import { ChangeDetectionStrategy, Component, computed, effect, input, signal } from '@angular/core';

/**
 * Le sigle della forma giuridica che precedono il nome: "A.S.D. PALOCCO"
 * deve dare la P, non la A che hanno metà delle società del Lazio.
 */
const SIGLE = new Set([
  'ASD', 'SSD', 'APD', 'ASC', 'ASDC', 'SSDARL', 'ARL', 'SRL', 'SPA', 'SS', 'US', 'AC', 'GS', 'SC',
  'AS', 'POL', 'CS', 'ACD', 'USD', 'FC', 'CD',
]);

/**
 * L'iniziale da mostrare al posto dello stemma: la prima lettera (o cifra)
 * della prima parola che non è una sigla. Una parola con dei punti in mezzo
 * ("A.S.D.", "S.R.L.") è una sigla anche se non è nell'elenco. Se il nome è
 * fatto solo di sigle si prende comunque il primo carattere: meglio una A
 * che un cerchio vuoto.
 */
export function inizialeDi(nome: string | null | undefined): string {
  const parole = (nome ?? '').trim().split(/\s+/).filter(Boolean);
  const primaVera = parole.find((parola) => {
    const pulita = parola.replace(/[^\p{L}\p{N}]/gu, '').toUpperCase();
    return pulita.length > 0 && !SIGLE.has(pulita) && !/\p{L}\.\p{L}/u.test(parola);
  });
  const lettera = (primaVera ?? parole.join('')).match(/[\p{L}\p{N}]/u);
  return lettera ? lettera[0].toUpperCase() : '?';
}

/**
 * Lo stemma di una società, o un cerchio con l'iniziale del nome quando non
 * c'è o non si carica.
 *
 * L'immagine sta sul portale della LND (play.lnd.it), che permette l'hotlink:
 * non passa da noi. `referrerpolicy="no-referrer"` perché al portale non
 * serve sapere da quale pagina dell'app arriva chi guarda, e `alt` è vuoto
 * perché il nome della società è sempre scritto accanto: letto due volte da
 * uno screen reader sarebbe solo rumore. Il segnaposto ha la stessa misura
 * dello stemma, così le righe dell'elenco restano allineate con e senza.
 */
@Component({
  selector: 'stemma-societa',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (mostraImmagine()) {
      <img
        [src]="logoUrl()"
        alt=""
        loading="lazy"
        decoding="async"
        referrerpolicy="no-referrer"
        [attr.width]="dimensione()"
        [attr.height]="dimensione()"
        (error)="nonCaricata.set(true)"
      />
    } @else {
      <span class="segnaposto" aria-hidden="true">{{ iniziale() }}</span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex: none;
      align-items: center;
      justify-content: center;
      width: var(--stemma-dimensione);
      height: var(--stemma-dimensione);
    }

    img {
      width: 100%;
      height: 100%;
      object-fit: contain;
    }

    /* Il cerchio con l'iniziale: il blu dell'app, tenue, per non gridare più
       degli stemmi veri che gli stanno accanto. */
    .segnaposto {
      display: flex;
      align-items: center;
      justify-content: center;
      width: 100%;
      height: 100%;
      border-radius: 50%;
      background: rgba(var(--ion-color-primary-rgb, 56, 128, 255), 0.12);
      color: var(--ion-color-primary-shade, #3171e0);
      font-weight: 700;
      font-size: calc(var(--stemma-dimensione) * 0.45);
      line-height: 1;
      user-select: none;
    }
  `,
  host: {
    '[style.--stemma-dimensione.px]': 'dimensione()',
  },
})
export class StemmaComponent {
  /** Il nome della società, da cui prendere l'iniziale del segnaposto. */
  readonly nome = input<string | null | undefined>('');
  /** L'indirizzo dello stemma; senza, o se non è https, il segnaposto. */
  readonly logoUrl = input<string | null | undefined>(undefined);
  /** Lato del quadrato, in pixel. */
  readonly dimensione = input(48);

  /** L'immagine ha dato errore: un 404 del portale, la rete che salta. */
  readonly nonCaricata = signal(false);

  readonly iniziale = computed(() => inizialeDi(this.nome()));

  readonly mostraImmagine = computed(() => {
    const indirizzo = this.logoUrl();
    return !!indirizzo && indirizzo.startsWith('https://') && !this.nonCaricata();
  });

  constructor() {
    // Nell'elenco il componente si ricicla su un'altra riga: l'errore dello
    // stemma di prima non deve nascondere quello nuovo.
    effect(() => {
      this.logoUrl();
      this.nonCaricata.set(false);
    });
  }
}
