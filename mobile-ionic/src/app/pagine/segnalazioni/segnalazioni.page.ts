import { DatePipe } from '@angular/common';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  IonBackButton,
  IonButton,
  IonButtons,
  IonCard,
  IonCardContent,
  IonContent,
  IonHeader,
  IonIcon,
  IonItem,
  IonLabel,
  IonList,
  IonSegment,
  IonSegmentButton,
  IonSpinner,
  IonTitle,
  IonToolbar,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { checkmarkCircle, refresh, warning } from 'ionicons/icons';
import {
  AutenticazioneService,
  RUOLO_AMMINISTRATORE,
} from '../../servizi/amministrazione/autenticazione.service';
import {
  SegnalazioneRicevuta,
  SegnalazioniService,
  TipoRettifica,
} from '../../servizi/segnalazioni/segnalazioni.service';

export const TESTO_TIPO: Record<TipoRettifica, string> = {
  NOME_SOCIETA: 'Nome società',
  INDIRIZZO_CAMPO: 'Indirizzo campo',
  ALTRO: 'Altro',
};

/**
 * Le richieste di rettifica arrivate dal form pubblico. Solo per chi
 * amministra: login sul Keycloak, ruolo trovacampo-admin, e il backend
 * comunque non darebbe l'elenco a nessun altro.
 */
@Component({
  selector: 'pagina-segnalazioni',
  imports: [
    DatePipe,
    RouterLink,
    IonBackButton,
    IonButton,
    IonButtons,
    IonCard,
    IonCardContent,
    IonContent,
    IonHeader,
    IonIcon,
    IonItem,
    IonLabel,
    IonList,
    IonSegment,
    IonSegmentButton,
    IonSpinner,
    IonTitle,
    IonToolbar,
  ],
  templateUrl: './segnalazioni.page.html',
})
export class SegnalazioniPage {
  private readonly service = inject(SegnalazioniService);
  readonly autenticazione = inject(AutenticazioneService);
  readonly ruolo = RUOLO_AMMINISTRATORE;
  readonly testoTipo = TESTO_TIPO;

  readonly accesso = signal<'in-corso' | 'entrato' | 'errore'>('in-corso');
  readonly dati = signal<'caricamento' | 'pronto' | 'errore'>('caricamento');
  readonly errore = signal<string | null>(null);
  readonly segnalazioni = signal<SegnalazioneRicevuta[]>([]);
  readonly mostra = signal<'da-gestire' | 'tutte'>('da-gestire');

  readonly daGestire = computed(() => this.segnalazioni().filter((s) => !s.gestita).length);
  readonly visibili = computed(() =>
    this.mostra() === 'tutte' ? this.segnalazioni() : this.segnalazioni().filter((s) => !s.gestita),
  );

  constructor() {
    addIcons({ checkmarkCircle, refresh, warning });
    this.entra();
  }

  entra(): void {
    this.accesso.set('in-corso');
    this.autenticazione.accedi().then(
      (entrato) => {
        this.accesso.set(entrato ? 'entrato' : 'errore');
        if (entrato && this.autenticazione.amministratore()) {
          this.carica();
        }
      },
      () => this.accesso.set('errore'),
    );
  }

  carica(): void {
    this.dati.set('caricamento');
    this.service.elenco().subscribe({
      next: (elenco) => {
        this.segnalazioni.set(elenco);
        this.dati.set('pronto');
      },
      error: (e: Error) => {
        this.errore.set(e.message);
        this.dati.set('errore');
      },
    });
  }

  segna(segnalazione: SegnalazioneRicevuta, gestita: boolean): void {
    this.service.segnaGestita(segnalazione.id, gestita).subscribe({
      next: (aggiornata) =>
        this.segnalazioni.update((tutte) =>
          tutte.map((s) => (s.id === aggiornata.id ? aggiornata : s)),
        ),
      error: (e: Error) => this.errore.set(e.message),
    });
  }
}
