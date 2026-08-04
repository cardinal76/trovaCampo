export interface Societa {
  id: string;
  siglaSocieta: string;
  nomeSocieta: string;
  comitatoRegionale: string;
  nomeImpianto: string;
  indirizzoImpianto: string;
  localitaImpianto: string;
  provinciaImpianto: string;
  lat?: number;
  lng?: number;
}

export interface NuovoCampoInput {
  nomeSocieta: string;
  nomeImpianto: string;
  indirizzoImpianto: string;
}
