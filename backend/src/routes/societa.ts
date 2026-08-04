import { Router } from "express";
import { societa } from "../data/societa";
import { NuovoCampoInput, Societa } from "../types";

const router = Router();

const DIACRITICI = /[̀-ͯ]/g;

function normalizza(testo: string): string {
  return testo.normalize("NFD").replace(DIACRITICI, "").toLowerCase().trim();
}

router.get("/societa", (req, res) => {
  const query = typeof req.query.nome === "string" ? req.query.nome : "";
  const termine = normalizza(query);

  if (!termine) {
    return res.json([]);
  }

  const risultati = societa.filter((s) => {
    const campiRicercabili = [s.nomeSocieta, s.nomeImpianto, s.indirizzoImpianto, s.localitaImpianto];
    return campiRicercabili.some((campo) => normalizza(campo).includes(termine));
  });

  res.json(risultati);
});

router.post("/societa", (req, res) => {
  const body = req.body as Partial<NuovoCampoInput>;
  const nomeSocieta = typeof body.nomeSocieta === "string" ? body.nomeSocieta.trim() : "";
  const nomeImpianto = typeof body.nomeImpianto === "string" ? body.nomeImpianto.trim() : "";
  const indirizzoImpianto =
    typeof body.indirizzoImpianto === "string" ? body.indirizzoImpianto.trim() : "";

  if (!nomeSocieta || !nomeImpianto || !indirizzoImpianto) {
    return res.status(400).json({
      errore: "nomeSocieta, nomeImpianto e indirizzoImpianto sono obbligatori",
    });
  }

  const nuovaSocieta: Societa = {
    id: crypto.randomUUID(),
    siglaSocieta: "",
    nomeSocieta,
    comitatoRegionale: "",
    nomeImpianto,
    indirizzoImpianto,
    localitaImpianto: "",
    provinciaImpianto: "",
  };

  societa.push(nuovaSocieta);

  res.status(201).json(nuovaSocieta);
});

export default router;
