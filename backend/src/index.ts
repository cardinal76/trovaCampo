import cors from "cors";
import express from "express";
import societaRouter from "./routes/societa";

const app = express();
const port = process.env.PORT ? Number(process.env.PORT) : 3000;

app.use(cors());
app.use("/api", societaRouter);

app.listen(port, () => {
  console.log(`TrovaCampo API in ascolto su http://localhost:${port}`);
});
