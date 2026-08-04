import { NavigationContainer } from "@react-navigation/native";
import { createNativeStackNavigator } from "@react-navigation/native-stack";
import { StatusBar } from "expo-status-bar";
import { RootStackParamList } from "./src/navigation";
import HomeScreen from "./src/screens/HomeScreen";
import InserisciCampoScreen from "./src/screens/InserisciCampoScreen";
import RisultatiScreen from "./src/screens/RisultatiScreen";

const Stack = createNativeStackNavigator<RootStackParamList>();

export default function App() {
  return (
    <NavigationContainer>
      <StatusBar style="auto" />
      <Stack.Navigator initialRouteName="Home">
        <Stack.Screen name="Home" component={HomeScreen} options={{ title: "TrovaCampo" }} />
        <Stack.Screen
          name="Risultati"
          component={RisultatiScreen}
          options={({ route }) => ({ title: route.params.query })}
        />
        <Stack.Screen
          name="Inserisci"
          component={InserisciCampoScreen}
          options={{ title: "Aggiungi campo" }}
        />
      </Stack.Navigator>
    </NavigationContainer>
  );
}
