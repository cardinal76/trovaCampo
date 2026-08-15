import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useState } from "react";
import {
  Keyboard,
  StyleSheet,
  Text,
  TouchableOpacity,
  TouchableWithoutFeedback,
  View,
} from "react-native";
import BarraRicerca from "../components/BarraRicerca";
import { RootStackParamList } from "../navigation";
import { colori, spazi } from "../theme";

type Props = NativeStackScreenProps<RootStackParamList, "Home">;

/**
 * Prima schermata della Funzione 1 (Ricerca campo): titolo, casella di
 * ricerca e pulsante "Vai", come nei mockup grafica/home.jpg e
 * grafica/FunzioneUnoSchermataUno.jpg.
 */
export default function HomeScreen({ navigation }: Props) {
  const [query, setQuery] = useState("");

  function cerca() {
    const termine = query.trim();
    if (termine.length === 0) {
      return;
    }
    Keyboard.dismiss();
    navigation.navigate("Risultati", { query: termine });
  }

  return (
    <TouchableWithoutFeedback onPress={Keyboard.dismiss} accessible={false}>
      <View style={styles.container}>
        <Text style={styles.titolo}>Cerca Campo</Text>

        <BarraRicerca valore={query} onChangeValore={setQuery} onCerca={cerca} />

        <TouchableOpacity
          style={styles.link}
          onPress={() => navigation.navigate("Inserisci")}
          accessibilityRole="button"
        >
          <Text style={styles.linkTesto}>Non trovi un campo? Aggiungilo</Text>
        </TouchableOpacity>
      </View>
    </TouchableWithoutFeedback>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colori.sfondo,
    alignItems: "center",
    paddingTop: 80,
    paddingHorizontal: spazi.l,
  },
  titolo: {
    fontSize: 32,
    fontWeight: "600",
    color: colori.testo,
    marginBottom: spazi.xl,
  },
  link: {
    marginTop: spazi.l,
  },
  linkTesto: {
    color: colori.primario,
    fontSize: 14,
  },
});
