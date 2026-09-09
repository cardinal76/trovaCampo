import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { indirizzoCompleto, nomeCompleto } from "../formato";
import { colori, spazi } from "../theme";
import { Societa } from "../types";

interface Props {
  societa: Societa;
  onPress: () => void;
}

/** Riga della lista dei risultati sotto la mappa. */
export default function VoceCampo({ societa, onPress }: Props) {
  const geolocalizzato = typeof societa.lat === "number" && typeof societa.lng === "number";

  return (
    <TouchableOpacity style={styles.voce} onPress={onPress} accessibilityRole="button">
      <View style={styles.intestazione}>
        <Text style={styles.nome}>{nomeCompleto(societa)}</Text>
        {!geolocalizzato && <Text style={styles.badge}>senza posizione</Text>}
      </View>
      <Text style={styles.impianto}>{societa.nomeImpianto}</Text>
      <Text style={styles.indirizzo}>{indirizzoCompleto(societa)}</Text>
    </TouchableOpacity>
  );
}

const styles = StyleSheet.create({
  voce: {
    paddingHorizontal: spazi.m,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: colori.bordoTenue,
  },
  intestazione: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  nome: {
    flex: 1,
    fontWeight: "600",
    color: colori.testo,
  },
  badge: {
    marginLeft: spazi.s,
    fontSize: 11,
    color: colori.testoTenue,
    textTransform: "uppercase",
  },
  impianto: {
    color: colori.testo,
  },
  indirizzo: {
    color: colori.testoTenue,
  },
});
