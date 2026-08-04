import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useState } from "react";
import { StyleSheet, Text, TextInput, View } from "react-native";
import { RootStackParamList } from "../navigation";

type Props = NativeStackScreenProps<RootStackParamList, "Home">;

export default function HomeScreen({ navigation }: Props) {
  const [query, setQuery] = useState("");

  function handleSubmit() {
    const termine = query.trim();
    if (termine.length === 0) {
      return;
    }
    navigation.navigate("Risultati", { query: termine });
  }

  return (
    <View style={styles.container}>
      <Text style={styles.titolo}>Cerca Campo</Text>
      <TextInput
        style={styles.input}
        placeholder="cerca per nome Società oppure cerca per indirizzo"
        value={query}
        onChangeText={setQuery}
        onSubmitEditing={handleSubmit}
        returnKeyType="search"
        autoCapitalize="none"
        autoCorrect={false}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#fff",
    alignItems: "center",
    paddingTop: 80,
    paddingHorizontal: 24,
  },
  titolo: {
    fontSize: 32,
    fontWeight: "600",
    marginBottom: 40,
  },
  input: {
    width: "100%",
    borderWidth: 1,
    borderColor: "#8fb4e0",
    borderRadius: 4,
    paddingVertical: 10,
    paddingHorizontal: 12,
    fontSize: 15,
    textAlign: "center",
  },
});
