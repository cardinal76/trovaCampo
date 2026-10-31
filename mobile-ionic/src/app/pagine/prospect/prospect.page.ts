import { DatePipe } from '@angular/common';
import {
  Component,
  ElementRef,
  NgZone,
  OnDestroy,
  computed,
  effect,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonIcon,
  IonNote,
  IonSelect,
  IonSelectOption,
  IonSpinner,
  IonText,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import {
  cloudDownload,
  link,
  location,
  pause,
  play,
  playForward,
  refresh,
  stop,
  warning,
} from 'ionicons/icons';
import * as L from 'leaflet';
import { campoSuCanvas, immagineCampo } from '../../mappa/icona-campo';
import {
  FiltriProspect,
  NESSUN_FILTRO,
  Prospect,
  TESTO_FONTE,
  filtraProspect,
  opzioniFiltri,
} from '../../modelli/prospect';
import { testoSicuro } from '../../modelli/societa';
import {
  AutenticazioneService,
  RUOLO_AMMINISTRATORE,
} from '../../servizi/autenticazione.service';
import {
  AmbitoDettagli,
  CoperturaProspect,
  FONTI_OPEN_DATA,
  FonteOpenData,
  ProspectService,
  StatoGeocodifica,
  StatoImportazioneProspect,
  TESTO_AMBITO,
  durataLeggibile,
} from '../../servizi/prospect.service';

const ITALIA = L.latLngBounds([36.6, 6.6], [47.1, 18.5]);
/** L'id del Lazio nel registro: il primo lotto. */
const LAZIO = 12;

/**
 * Le ASD/SSD del Registro nazionale su una mappa, con i filtri per regione,
 * disciplina e organismo: una lista commerciale, quindi **solo per chi
 * amministra**. Non è collegata dalle pagine pubbliche, apre il login di
 * Keycloak, e senza il ruolo trovacampo-admin non chiede nemmeno i dati (che
 * il backend comunque non darebbe).
 *
 * La mappa è quella della pagina Mappa: Leaflet su canvas, con la stessa
 * icona del campo, perché i prospect possono essere migliaia.
 */
@Component({
  selector: 'pagina-prospect',
  imports: [
    DatePipe,
    IonBackButton,
    IonButton,
    IonButtons,
    IonCard,
    IonCardContent,
    IonContent,
    IonHeader,
    IonIcon,
    IonNote,
    IonSelect,
    IonSelectOption,
    IonSpinner,
    IonText,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './prospect.page.html',
  styleUrl: './prospect.page.scss',
})
export class ProspectPage implements OnDestroy {
  private readonly service = inject(ProspectService);
  private readonly zona = inject(NgZone);
  readonly autenticazione = inject(AutenticazioneService);
  readonly ruolo = RUOLO_AMMINISTRATORE;

  private readonly contenitore = viewChild<ElementRef<HTMLElement>>('contenitoreMappa');
  private mappa: L.Map | null = null;
  private livello: L.LayerGroup | null = null;
  private distrutta = false;

  readonly accesso = signal<'in-corso' | 'entrato' | 'errore'>('in-corso');
  readonly dati = signal<'caricamento' | 'pronto' | 'errore'>('caricamento');
  readonly errore = signal<string | null>(null);
  readonly prospect = signal<Prospect[]>([]);
  readonly stato = signal<StatoImportazioneProspect | null>(null);
  readonly filtri = signal<FiltriProspect>(NESSUN_FILTRO);
  readonly copertura = signal<CoperturaProspect | null>(null);
  readonly geocodifica = signal<StatoGeocodifica | null>(null);
  /** L'esito dell'ultima azione sugli indirizzi, da mostrare sotto i pulsanti. */
  readonly avviso = signal<string | null>(null);
  readonly fontiOpenData = FONTI_OPEN_DATA;
  /** Il censimento open data da importare: Roma, come prima che ci fossero gli altri. */
  readonly fonteOpenData = signal<FonteOpenData>('cmrc');
  readonly testoFonte = TESTO_FONTE;
  readonly fonti = Object.keys(TESTO_FONTE) as (keyof typeof TESTO_FONTE)[];

  /** Per chi chiedere il dettaglio al prossimo lancio. */
  readonly ambito = signal<AmbitoDettagli>('CALCIO');
  readonly testoAmbito = TESTO_AMBITO;
  readonly ambiti = Object.keys(TESTO_AMBITO) as AmbitoDettagli[];

  /** La fase, detta per esteso: in pausa per la finestra, fermo per un blocco… */
  readonly fase = computed(() => {
    const s = this.stato();
    if (!s) {
      return null;
    }
    const fase = s.fase ?? (s.inCorso ? 'in corso' : s.ultimo.stato);
    const alle = s.pausaFinoA ? orario(s.pausaFinoA) : '';
    switch (fase) {
      case 'in pausa: finestra oraria':
        return `In pausa: fuori dalla finestra oraria (${s.ritmo?.finestra ?? ''}), riparte da sola${alle ? ' ' + alle : ''}`;
      case 'in pausa: tetto giornaliero':
        return `In pausa: raggiunto il tetto di ${s.ritmo?.tettoGiornaliero ?? ''} richieste di oggi, riparte da sola${alle ? ' ' + alle : ''}`;
      case 'bloccata':
        return 'Ferma per un blocco del registro: non riprova da sola';
      case 'interrotta':
        return 'Interrotta da un riavvio del backend: «Riprendi» la fa ripartire da dove era';
      case 'mai':
        return 'Mai lanciata';
      default:
        return fase.charAt(0).toUpperCase() + fase.slice(1);
    }
  });

  /** «circa 14 giorni» al ritmo configurato, per quello che resta del giro. */
  readonly tempoRimanente = computed(() => {
    const stima = this.stato()?.stima;
    return stima ? durataLeggibile(stima.secondi) : null;
  });

  readonly opzioni = computed(() => opzioniFiltri(this.prospect()));
  readonly visibili = computed(() => filtraProspect(this.prospect(), this.filtri()));

  constructor() {
    addIcons({ cloudDownload, link, location, pause, play, playForward, refresh, stop, warning });
    this.entra();

    effect(() => {
      const contenitore = this.contenitore()?.nativeElement;
      const visibili = this.visibili();
      if (contenitore && this.dati() === 'pronto') {
        immagineCampo().then((immagine) => {
          // L'immagine può arrivare a pagina già lasciata: una mappa creata
          // allora non verrebbe più tolta.
          if (!this.distrutta && this.contenitore()?.nativeElement === contenitore) {
            this.disegna(contenitore, visibili, immagine);
          }
        });
      }
    });
  }

  ngOnDestroy(): void {
    this.distrutta = true;
    const mappa = this.mappa;
    this.mappa = null;
    this.livello = null;
    // Al fotogramma dopo: cambiare i filtri chiede al canvas un ridisegno per
    // il fotogramma successivo, e Leaflet non sempre riesce ad annullarlo
    // togliendo la mappa; il ridisegno su un canvas già tolto fallirebbe.
    if (mappa) {
      requestAnimationFrame(() => mappa.remove());
    }
  }

  entra(): void {
    this.accesso.set('in-corso');
    this.autenticazione.accedi().then(
      (entrato) => {
        this.accesso.set(entrato ? 'entrato' : 'errore');
        // Senza il ruolo non si chiede niente: la pagina dice solo cosa manca.
        if (entrato && this.autenticazione.amministratore()) {
          this.carica();
        }
      },
      () => this.accesso.set('errore'),
    );
  }

  carica(): void {
    this.dati.set('caricamento');
    this.service.sullaMappa().subscribe({
      next: (prospect) => {
        this.prospect.set(prospect);
        this.dati.set('pronto');
      },
      error: (e: Error) => {
        this.errore.set(e.message);
        this.dati.set('errore');
      },
    });
    this.aggiornaStato();
  }

  aggiornaStato(): void {
    this.service.stato().subscribe({
      next: (stato) => this.stato.set(stato),
      error: (e: Error) => this.errore.set(e.message),
    });
    this.service.copertura().subscribe({
      next: (copertura) => this.copertura.set(copertura),
      error: (e: Error) => this.errore.set(e.message),
    });
    this.service.statoGeocodifica().subscribe({
      next: (stato) => this.geocodifica.set(stato),
      error: (e: Error) => this.errore.set(e.message),
    });
  }

  /** Campi LND e censimento open data, subito. */
  collega(): void {
    this.errore.set(null);
    this.service.collega().subscribe({
      next: (copertura) => {
        this.copertura.set(copertura);
        this.avviso.set(`Collegamento fatto: ${copertura.collegati} su ${copertura.prospectCalcioLazio}`);
      },
      error: (e: Error) => this.errore.set(e.message),
    });
  }

  scegliFonteOpenData(fonte: FonteOpenData): void {
    this.fonteOpenData.set(fonte);
  }

  importaOpenData(): void {
    const fonte = this.fonteOpenData();
    this.errore.set(null);
    this.avviso.set(
      fonte === 'veneto'
        ? 'Importazione in corso: un file per comune, dieci secondi l\'uno dall\'altro (un paio di minuti)…'
        : 'Importazione in corso…',
    );
    this.service.importaOpenData(fonte).subscribe({
      next: (esito) => {
        const nonLetti = esito.nonLetti?.length
          ? ` Non letti: ${esito.nonLetti.length} (${esito.nonLetti.join('; ')}).`
          : '';
        this.avviso.set(
          `Censimento importato: ${esito.impianti} impianti con calcio, ${esito.conCoordinate ?? 0} con la ` +
            `posizione (${esito.licenza}).${nonLetti} Ora «Collega ai campi».`,
        );
      },
      error: (e: Error) => {
        this.avviso.set(null);
        this.errore.set(e.message);
      },
    });
  }

  /** OpenStreetMap per chi è ancora senza indirizzo, in sottofondo. */
  cercaSuOsm(): void {
    this.errore.set(null);
    this.service.geocodifica().subscribe({
      next: (avvio) => {
        this.geocodifica.set(avvio.stato);
        this.avviso.set(avvio.messaggio);
      },
      error: (e: Error) => this.errore.set(e.message),
    });
  }

  filtra(campo: keyof FiltriProspect, valore: string | null | undefined): void {
    this.filtri.update((f) => ({ ...f, [campo]: valore ?? '' }));
  }

  importaLazio(): void {
    this.avvia([LAZIO]);
  }

  importaItalia(): void {
    this.avvia([]);
  }

  /** L'ultimo giro, dalla posizione salvata: dopo un blocco lo decide una persona. */
  riprendi(): void {
    this.errore.set(null);
    this.service.riprendi().subscribe({
      next: (stato) => this.stato.set(stato),
      error: (e: Error) => this.errore.set(e.message),
    });
  }

  private avvia(regioni: number[]): void {
    this.errore.set(null);
    this.service.avvia(regioni, undefined, this.ambito()).subscribe({
      next: (stato) => this.stato.set(stato),
      error: (e: Error) => this.errore.set(e.message),
    });
  }

  ferma(): void {
    this.service.ferma().subscribe({
      next: (stato) => this.stato.set(stato),
      error: (e: Error) => this.errore.set(e.message),
    });
  }

  esporta(): void {
    this.service.esporta().subscribe({
      next: (file) => {
        const link = document.createElement('a');
        link.href = URL.createObjectURL(file);
        link.download = 'prospect_italia.xlsx';
        link.click();
        setTimeout(() => URL.revokeObjectURL(link.href), 1000);
      },
      error: (e: Error) => this.errore.set(e.message),
    });
  }

  private disegna(contenitore: HTMLElement, prospect: Prospect[], immagine: HTMLImageElement): void {
    this.zona.runOutsideAngular(() => {
      if (this.mappa?.getContainer() !== contenitore) {
        this.mappa?.remove();
        this.mappa = L.map(contenitore, { preferCanvas: true });
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution: '© OpenStreetMap',
        }).addTo(this.mappa);
        this.livello = null;
      }
      const mappa = this.mappa!;
      // Prima la vista, poi i segnaposto: il renderer canvas nasce con la vista,
      // e un livello aggiunto prima chiederebbe di ridisegnare un canvas che non c'è.
      const limiti = L.latLngBounds(prospect.map((p) => [p.lat, p.lng] as L.LatLngTuple));
      mappa.fitBounds(prospect.length ? limiti : ITALIA, {
        padding: [20, 20],
        maxZoom: 14,
        animate: false,
      });
      this.livello?.remove();
      this.livello = L.layerGroup(
        prospect.map((p) => campoSuCanvas([p.lat, p.lng], immagine).bindPopup(popup(p))),
      ).addTo(mappa);
      setTimeout(() => {
        if (this.mappa === mappa) {
          mappa.invalidateSize();
        }
      }, 200);
    });
  }
}

/** «alle 22:00» oggi, «il 16/10 alle 00:00» un altro giorno. */
function orario(iso: string): string {
  const quando = new Date(iso);
  const ora = quando.toLocaleTimeString('it-IT', { hour: '2-digit', minute: '2-digit' });
  if (quando.toDateString() === new Date().toDateString()) {
    return `alle ${ora}`;
  }
  const giorno = quando.toLocaleDateString('it-IT', { day: '2-digit', month: '2-digit' });
  return `il ${giorno} alle ${ora}`;
}

function popup(p: Prospect): string {
  const righe = [
    `<strong>${testoSicuro(p.denominazione)}</strong>`,
    `CF ${testoSicuro(p.codiceFiscale)} · ${testoSicuro(p.comune ?? '')}`,
  ];
  if (p.nomeImpianto || p.indirizzo) {
    righe.push(
      testoSicuro([p.nomeImpianto, p.indirizzo, p.comuneCampo].filter(Boolean).join(', ')),
    );
  }
  if (p.organismi?.length) {
    righe.push(`Organismi: ${testoSicuro(p.organismi.join(', '))}`);
  }
  if (p.discipline?.length) {
    righe.push(`Discipline: ${testoSicuro(p.discipline.join(', '))}`);
  }
  if (typeof p.tesseratiAttivi === 'number') {
    righe.push(`Tesserati attivi: ${p.tesseratiAttivi}`);
  }
  if (p.fonteIndirizzo) {
    righe.push(`<small>Indirizzo: ${TESTO_FONTE[p.fonteIndirizzo]}</small>`);
  }
  return righe.join('<br>');
}
