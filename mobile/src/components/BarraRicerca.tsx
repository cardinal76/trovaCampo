import { StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { colori, raggio, spazi } from "../theme";

export const PLACEHOLDER_RICERCA = "cerca per nome Società oppure cerca per indirizzo";

interface Props {
  valore: string;
  onChangeValore: (valore: string) => void;
  onCerca: () => void;
  /**
   * "estesa" è la barra della Home (mockup grafica/home.jpg): casella larga
   * con il pulsante "Vai" sotto. "compatta" è quella in testa ai risultati,
   * che permette di correggere la ricerca senza tornare indietro.
   */
  variante?: "estesa" | "compatta";
  autoFocus?: boolean;
}

export default function BarraRicerca({
  valore,
  onChangeValore,
  onCerca,
  variante = "estesa",
  autoFocus = false,
}: Props) {
  const compatta = variante === "compatta";
  const attivo = valore.trim().length > 0;

  function handleCerca() {
    if (attivo) {
      onCerca();
    }
  }

  return (
    <View style={compatta ? styles.contenitoreCompatto : styles.contenitoreEsteso}>
      <TextInput
        style={[styles.input, compatta ? styles.inputCompatto : styles.inputEsteso]}
        placeholder={PLACEHOLDER_RICERCA}
        placeholderTextColor={colori.testoTenue}
        value={valore}
        onChangeText={onChangeValore}
        onSubmitEditing={handleCerca}
        returnKeyType="search"
        autoCapitalize="none"
        autoCorrect={false}
        autoFocus={autoFocus}
        clearButtonMode="while-editing"
        accessibilityLabel="Nome società o indirizzo del campo"
      />
      <TouchableOpacity
        style={[
          styles.bottone,
          compatta ? styles.bottoneCompatto : styles.bottoneEsteso,
          !attivo && styles.bottoneDisabilitato,
        ]}
        onPress={handleCerca}
        disabled={!attivo}
        accessibilityRole="button"
        accessibilityLabel="Cerca il campo"
      >
        <Text style={styles.bottoneTesto}>Vai</Text>
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  contenitoreEsteso: {
    width: "100%",
    alignItems: "center",
  },
  contenitoreCompatto: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: spazi.m,
    paddingVertical: spazi.s,
    backgroundColor: colori.sfondo,
    borderBottomWidth: 1,
    borderBottomColor: colori.bordoTenue,
  },
  input: {
    borderWidth: 1,
    borderColor: colori.bordo,
    borderRadius: raggio,
    paddingVertical: 10,
    paddingHorizontal: 12,
    fontSize: 15,
    color: colori.testo,
  },
  inputEsteso: {
    width: "100%",
    textAlign: "center",
  },
  inputCompatto: {
    flex: 1,
  },
  bottone: {
    backgroundColor: colori.primario,
    borderRadius: raggio,
    alignItems: "center",
    justifyContent: "center",
  },
  bottoneEsteso: {
    marginTop: spazi.m,
    paddingVertical: 10,
    paddingHorizontal: spazi.xl,
  },
  bottoneCompatto: {
    marginLeft: spazi.s,
    paddingVertical: 10,
    paddingHorizontal: spazi.m,
  },
  bottoneDisabilitato: {
    backgroundColor: colori.disabilitato,
  },
  bottoneTesto: {
    color: colori.sfondo,
    fontWeight: "600",
    fontSize: 15,
  },
});
