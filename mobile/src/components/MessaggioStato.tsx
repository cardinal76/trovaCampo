import { StyleSheet, Text, TouchableOpacity, View } from "react-native";
import { colori, raggio, spazi } from "../theme";

interface Props {
  testo: string;
  azione?: { etichetta: string; onPress: () => void };
}

/** Stato vuoto / di errore della ricerca, con un'azione di uscita. */
export default function MessaggioStato({ testo, azione }: Props) {
  return (
    <View style={styles.contenitore}>
      <Text style={styles.testo}>{testo}</Text>
      {azione && (
        <TouchableOpacity
          style={styles.bottone}
          onPress={azione.onPress}
          accessibilityRole="button"
        >
          <Text style={styles.bottoneTesto}>{azione.etichetta}</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  contenitore: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spazi.l,
  },
  testo: {
    textAlign: "center",
    color: colori.testo,
  },
  bottone: {
    marginTop: spazi.m,
    borderWidth: 1,
    borderColor: colori.primario,
    borderRadius: raggio,
    paddingVertical: spazi.s,
    paddingHorizontal: spazi.m,
  },
  bottoneTesto: {
    color: colori.primario,
    fontWeight: "600",
  },
});
