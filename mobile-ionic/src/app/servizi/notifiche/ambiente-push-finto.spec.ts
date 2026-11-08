import { IscrizionePush, PermessoPosizione, SupportoPush } from './ambiente-push';

/** L'iscrizione che il browser finto dà a chi si iscrive. */
export const ISCRIZIONE_FINTA: IscrizionePush = {
  endpoint: 'https://fcm.googleapis.com/fcm/send/finto',
  keys: { p256dh: 'BPchiave', auth: 'segreto' },
};

/**
 * Il browser per i test delle notifiche: niente service worker veri né
 * permessi veri, solo lo stato che il test decide. Condiviso fra le spec
 * (sta in un file .spec.ts perché non finisca nella build dell'app).
 */
export class AmbientePushFinto {
  supportoAttuale: SupportoPush = 'supportato';
  permessoAttuale: NotificationPermission = 'default';
  /** Cosa risponde chi usa l'app quando il browser chiede il permesso. */
  risposta: NotificationPermission = 'granted';
  permessoGps: PermessoPosizione = 'prompt';
  iscrizione: IscrizionePush | null = null;
  chiaveUsata: string | null = null;

  readonly registra = jasmine.createSpy('registra').and.resolveTo();
  readonly chiediPermesso = jasmine.createSpy('chiediPermesso').and.callFake(async () => {
    this.permessoAttuale = this.risposta;
    return this.risposta;
  });

  supporto(): SupportoPush {
    return this.supportoAttuale;
  }

  permesso(): NotificationPermission {
    return this.permessoAttuale;
  }

  async iscrivi(chiavePubblica: string): Promise<IscrizionePush> {
    this.chiaveUsata = chiavePubblica;
    this.iscrizione = ISCRIZIONE_FINTA;
    return ISCRIZIONE_FINTA;
  }

  async iscrizioneAttuale(): Promise<IscrizionePush | null> {
    return this.iscrizione;
  }

  async disiscrivi(): Promise<void> {
    this.iscrizione = null;
  }

  async permessoPosizione(): Promise<PermessoPosizione> {
    return this.permessoGps;
  }
}

/** Lascia finire le promesse in corso: le chiamate HTTP partono dopo qualche await. */
export async function attendi(): Promise<void> {
  await new Promise((risolvi) => setTimeout(risolvi, 0));
}
