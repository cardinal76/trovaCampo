import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useEffect, useState } from "react";
import { ActivityIndicator, ScrollView, StyleSheet, Text, View } from "react-native";
import { ottieniSocieta } from "../api/societa";
import { indirizzoCompleto, nomeCompleto } from "../formato";
import { RootStackParamList } from "../navigation";
import { Campionato, Societa } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "Dettaglio">;

function Riga({ etichetta, valore }: { etichetta: string; valore?: string }) {
  if (!valore) {
    return null;
  }
  return (
    <View style={styles.riga}>
      <Text style={styles.rigaEtichetta}>{etichetta}</Text>
      <Text style={styles.rigaValore}>{valore}</Text>
    </View>
  );
}

function SezioneCampionati({ titolo, campionati }: { titolo: string; campionati: Campionato[] }) {
  if (campionati.length === 0) {
    return null;
  }
  return (
    <View style={styles.sezione}>
      <Text style={styles.sezioneTitolo}>{titolo}</Text>
      {campionati.map((c, indice) => (
        <View key={`${c.descrizione}-${indice}`} style={styles.campionato}>
          <Text style={styles.campionatoDescrizione}>{c.descrizione}</Text>
          <Text style={styles.campionatoDettaglio}>
            Girone {c.girone} · Comitato {c.comitato}
          </Text>
        </View>
      ))}
    </View>
  );
}

export default function DettaglioSocietaScreen({ route }: Props) {
  const { id } = route.params;
  const [societa, setSocieta] = useState<Societa | null>(null);
  const [caricamento, setCaricamento] = useState(true);
  const [errore, setErrore] = useState<string | null>(null);

  useEffect(() => {
    let attivo = true;

    setCaricamento(true);
    setErrore(null);

    ottieniSocieta(id)
      .then((dati) => {
        if (attivo) {
          setSocieta(dati);
        }
      })
      .catch(() => {
        if (attivo) {
          setErrore("Impossibile caricare la scheda della società. Riprova.");
        }
      })
      .finally(() => {
        if (attivo) {
          setCaricamento(false);
        }
      });

    return () => {
      attivo = false;
    };
  }, [id]);

  if (caricamento) {
    return (
      <View style={styles.centrato}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (errore || !societa) {
    return (
      <View style={styles.centrato}>
        <Text>{errore ?? "Società non trovata"}</Text>
      </View>
    );
  }

  const haAnagrafica = Boolean(
    societa.presidente ||
      societa.indirizzoSede ||
      societa.telefono ||
      societa.email ||
      societa.sitoWeb ||
      societa.matricola
  );

  const campionati = societa.campionati ?? [];
  const campionatiAgonistica = campionati.filter((c) => c.tipo === "Agonistica");
  const campionatiScuolaCalcio = campionati.filter((c) => c.tipo === "ScuolaCalcio");

  return (
    <ScrollView style={styles.container} contentContainerStyle={styles.contenuto}>
      <Text style={styles.titolo}>{nomeCompleto(societa)}</Text>

      <View style={styles.sezione}>
        <Text style={styles.sezioneTitolo}>Campo</Text>
        <Riga etichetta="Impianto" valore={societa.nomeImpianto} />
        <Riga etichetta="Indirizzo" valore={indirizzoCompleto(societa)} />
      </View>

      {haAnagrafica ? (
        <View style={styles.sezione}>
          <Text style={styles.sezioneTitolo}>Anagrafica società</Text>
          <Riga etichetta="Matricola" valore={societa.matricola} />
          <Riga etichetta="Comitato regionale" valore={societa.comitatoRegionale} />
          <Riga etichetta="Presidente" valore={societa.presidente} />
          <Riga etichetta="Indirizzo sede" valore={societa.indirizzoSede} />
          <Riga etichetta="Telefono" valore={societa.telefono} />
          <Riga etichetta="Fax" valore={societa.fax} />
          <Riga etichetta="Email" valore={societa.email} />
          <Riga etichetta="Sito web" valore={societa.sitoWeb} />
          <Riga
            etichetta="Scuola calcio"
            valore={
              societa.scuolaCalcio === undefined
                ? undefined
                : societa.scuolaCalcio
                  ? `Sì${societa.prezziScuolaCalcio ? ` — ${societa.prezziScuolaCalcio}` : ""}`
                  : "No"
            }
          />
        </View>
      ) : (
        <View style={styles.sezione}>
          <Text style={styles.sezioneTitolo}>Anagrafica società</Text>
          <Text style={styles.assente}>
            Anagrafica non ancora disponibile per questo campo (inserito manualmente).
          </Text>
        </View>
      )}

      <SezioneCampionati titolo="Campionati agonistica" campionati={campionatiAgonistica} />
      <SezioneCampionati titolo="Campionati scuola calcio" campionati={campionatiScuolaCalcio} />
      {campionati.length === 0 && (
        <View style={styles.sezione}>
          <Text style={styles.sezioneTitolo}>Campionati</Text>
          <Text style={styles.assente}>Nessun campionato registrato.</Text>
        </View>
      )}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
  },
  contenuto: {
    padding: 20,
  },
  titolo: {
    fontSize: 22,
    fontWeight: "600",
    marginBottom: 16,
  },
  sezione: {
    marginBottom: 24,
  },
  sezioneTitolo: {
    fontSize: 13,
    fontWeight: "600",
    color: "#666",
    textTransform: "uppercase",
    marginBottom: 8,
  },
  riga: {
    flexDirection: "row",
    justifyContent: "space-between",
    paddingVertical: 6,
    borderBottomWidth: 1,
    borderBottomColor: "#f0f0f0",
  },
  rigaEtichetta: {
    color: "#666",
    flex: 1,
  },
  rigaValore: {
    flex: 2,
    textAlign: "right",
  },
  assente: {
    color: "#999",
    fontStyle: "italic",
  },
  campionato: {
    paddingVertical: 8,
    borderBottomWidth: 1,
    borderBottomColor: "#f0f0f0",
  },
  campionatoDescrizione: {
    fontWeight: "500",
  },
  campionatoDettaglio: {
    color: "#666",
    fontSize: 13,
  },
  centrato: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 24,
  },
});
