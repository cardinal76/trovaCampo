import { NuovoCampoInput, Societa } from "../types";

// In sviluppo puntare all'IP della macchina che esegue il backend, es.
// EXPO_PUBLIC_API_URL=http://192.168.1.10:3000 expo start
const API_URL = process.env.EXPO_PUBLIC_API_URL ?? "http://localhost:3000";

export async function cercaSocieta(nome: string): Promise<Societa[]> {
  const url = `${API_URL}/api/societa?nome=${encodeURIComponent(nome)}`;
  const risposta = await fetch(url);

  if (!risposta.ok) {
    throw new Error(`Ricerca fallita (${risposta.status})`);
  }

  return risposta.json();
}

export async function ottieniSocieta(id: string): Promise<Societa> {
  const risposta = await fetch(`${API_URL}/api/societa/${encodeURIComponent(id)}`);

  if (!risposta.ok) {
    throw new Error(`Recupero società fallito (${risposta.status})`);
  }

  return risposta.json();
}

export async function inserisciSocieta(campo: NuovoCampoInput): Promise<Societa> {
  const risposta = await fetch(`${API_URL}/api/societa`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(campo),
  });

  if (!risposta.ok) {
    throw new Error(`Inserimento fallito (${risposta.status})`);
  }

  return risposta.json();
}
