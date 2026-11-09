import { paginaAccesso, stemmaAccesso } from './presenze';

describe('paginaAccesso', () => {
  it('porta alla pagina di accesso del club su presenze', () => {
    expect(paginaAccesso({ alias: 'real-fontanella' })).toBe(
      'https://presenze.footballer.it/?societa=real-fontanella',
    );
  });

  it('senza accesso, o con un alias che non è uno slug, non c è nessun indirizzo', () => {
    expect(paginaAccesso(null)).toBeNull();
    expect(paginaAccesso(undefined)).toBeNull();
    expect(paginaAccesso({ alias: '' })).toBeNull();
    expect(paginaAccesso({ alias: 'Real Fontanella' })).toBeNull();
    expect(paginaAccesso({ alias: 'a&b=c' })).toBeNull();
  });
});

describe('stemmaAccesso', () => {
  it('completa il percorso degli stemmi con l origine di presenze', () => {
    expect(stemmaAccesso({ alias: 'a', stemma: '/api/organizzazioni/3/logo' })).toBe(
      'https://presenze.footballer.it/api/organizzazioni/3/logo',
    );
  });

  it('lascia passare un indirizzo https e nient altro', () => {
    expect(stemmaAccesso({ alias: 'a', stemma: 'https://play.lnd.it/a.png' })).toBe(
      'https://play.lnd.it/a.png',
    );
    expect(stemmaAccesso({ alias: 'a', stemma: 'http://tracker.example/a.png' })).toBeNull();
    expect(stemmaAccesso({ alias: 'a', stemma: '/api/admin/altro' })).toBeNull();
    expect(stemmaAccesso({ alias: 'a', stemma: null })).toBeNull();
    expect(stemmaAccesso(null)).toBeNull();
  });
});
