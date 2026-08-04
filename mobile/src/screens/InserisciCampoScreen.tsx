import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useState } from "react";
import { ActivityIndicator, Alert, StyleSheet, Text, TextInput, TouchableOpacity, View } from "react-native";
import { inserisciSocieta } from "../api/societa";
import { RootStackParamList } from "../navigation";

type Props = NativeStackScreenProps<RootStackParamList, "Inserisci">;

export default function InserisciCampoScreen({ navigation }: Props) {
  const [nomeCampo, setNomeCampo] = useState("");
  const [nomeSocieta, setNomeSocieta] = useState("");
  const [indirizzo, setIndirizzo] = useState("");
  const [salvataggio, setSalvataggio] = useState(false);

  const pronto = nomeCampo.trim() && nomeSocieta.trim() && indirizzo.trim();

  async function handleSalva() {
    if (!pronto || salvataggio) {
      return;
    }

    setSalvataggio(true);
    try {
      await inserisciSocieta({
        nomeImpianto: nomeCampo.trim(),
        nomeSocieta: nomeSocieta.trim(),
        indirizzoImpianto: indirizzo.trim(),
      });
      Alert.alert("Campo aggiunto", "Grazie per il contributo!", [
        { text: "OK", onPress: () => navigation.goBack() },
      ]);
    } catch {
      Alert.alert("Errore", "Non è stato possibile salvare il campo. Riprova.");
    } finally {
      setSalvataggio(false);
    }
  }

  return (
    <View style={styles.container}>
      <Text style={styles.titolo}>Aggiungi campo</Text>
      <Text style={styles.suggerimento}>
        Puoi usare il microfono della tastiera per dettare il testo in ogni campo.
      </Text>

      <Text style={styles.etichetta}>Nome campo</Text>
      <TextInput
        style={styles.input}
        placeholder="es. Campo Certosa"
        value={nomeCampo}
        onChangeText={setNomeCampo}
        returnKeyType="next"
      />

      <Text style={styles.etichetta}>Nome società</Text>
      <TextInput
        style={styles.input}
        placeholder="es. A.S.D. Certosa Calcio"
        value={nomeSocieta}
        onChangeText={setNomeSocieta}
        returnKeyType="next"
      />

      <Text style={styles.etichetta}>Indirizzo</Text>
      <TextInput
        style={styles.input}
        placeholder="es. Via della Certosa 12, Roma"
        value={indirizzo}
        onChangeText={setIndirizzo}
        returnKeyType="done"
        onSubmitEditing={handleSalva}
      />

      <TouchableOpacity
        style={[styles.bottone, !pronto && styles.bottoneDisabilitato]}
        onPress={handleSalva}
        disabled={!pronto || salvataggio}
      >
        {salvataggio ? (
          <ActivityIndicator color="#fff" />
        ) : (
          <Text style={styles.bottoneTesto}>Salva</Text>
        )}
      </TouchableOpacity>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    paddingTop: 32,
    paddingHorizontal: 24,
  },
  titolo: {
    fontSize: 26,
    fontWeight: "600",
    marginBottom: 8,
  },
  suggerimento: {
    color: "#666",
    marginBottom: 24,
  },
  etichetta: {
    fontSize: 13,
    color: "#333",
    marginBottom: 6,
  },
  input: {
    borderWidth: 1,
    borderColor: "#8fb4e0",
    borderRadius: 4,
    paddingVertical: 10,
    paddingHorizontal: 12,
    fontSize: 15,
    marginBottom: 20,
  },
  bottone: {
    backgroundColor: "#2f6fce",
    borderRadius: 4,
    paddingVertical: 14,
    alignItems: "center",
    marginTop: 8,
  },
  bottoneDisabilitato: {
    opacity: 0.5,
  },
  bottoneTesto: {
    color: "#fff",
    fontWeight: "600",
    fontSize: 16,
  },
});
