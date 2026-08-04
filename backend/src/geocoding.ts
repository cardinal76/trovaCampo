/**
 * Geocodifica un indirizzo usando Nominatim (OpenStreetMap): servizio
 * gratuito e senza chiave API, ma soggetto alla sua usage policy
 * (https://operations.osmfoundation.org/policies/nominatim/): max 1
 * richiesta al secondo, niente uso massivo/bulk, User-Agent obbligatorio
 * che identifichi l'applicazione. Per volumi più alti servirebbe un
 * provider a pagamento o un'istanza Nominatim self-hosted.
 */

const NOMINATIM_URL = "https://nominatim.openstreetmap.org/search";
const USER_AGENT = "TrovaCampo/0.1 (progetto di analisi calcio dilettantistico)";
const TIMEOUT_MS = 5000;

export interface Coordinate {
  lat: number;
  lng: number;
}

export async function geocodifica(indirizzo: string): Promise<Coordinate | null> {
  const url = `${NOMINATIM_URL}?format=json&limit=1&q=${encodeURIComponent(indirizzo)}`;
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), TIMEOUT_MS);

  try {
    const risposta = await fetch(url, {
      headers: { "User-Agent": USER_AGENT },
      signal: controller.signal,
    });

    if (!risposta.ok) {
      return null;
    }

    const risultati = (await risposta.json()) as Array<{ lat: string; lon: string }>;
    if (risultati.length === 0) {
      return null;
    }

    const lat = Number(risultati[0].lat);
    const lng = Number(risultati[0].lon);
    if (Number.isNaN(lat) || Number.isNaN(lng)) {
      return null;
    }

    return { lat, lng };
  } catch {
    return null;
  } finally {
    clearTimeout(timeout);
  }
}
