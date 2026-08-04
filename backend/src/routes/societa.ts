import { Router } from "express";
import { societa } from "../data/societa";

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
    const campiRicercabili = [s.nomeSocieta, s.indirizzoImpianto, s.localitaImpianto];
    return campiRicercabili.some((campo) => normalizza(campo).includes(termine));
  });

  res.json(risultati);
});

export default router;
