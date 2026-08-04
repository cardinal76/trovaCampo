import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useEffect, useRef, useState } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import MapView, { Callout, Marker } from "react-native-maps";
import { cercaSocieta } from "../api/societa";
import { RootStackParamList } from "../navigation";
import { Societa } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "Risultati">;

export default function RisultatiScreen({ route }: Props) {
  const { query } = route.params;
  const [risultati, setRisultati] = useState<Societa[]>([]);
  const [caricamento, setCaricamento] = useState(true);
  const [errore, setErrore] = useState<string | null>(null);
  const mapRef = useRef<MapView>(null);

  useEffect(() => {
    let attivo = true;

    setCaricamento(true);
    setErrore(null);

    cercaSocieta(query)
      .then((dati) => {
        if (attivo) {
          setRisultati(dati);
        }
      })
      .catch(() => {
        if (attivo) {
          setErrore("Impossibile completare la ricerca. Riprova.");
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
  }, [query]);

  useEffect(() => {
    if (risultati.length > 0 && mapRef.current) {
      mapRef.current.fitToCoordinates(
        risultati.map((s) => ({ latitude: s.lat, longitude: s.lng })),
        { edgePadding: { top: 80, right: 80, bottom: 80, left: 80 }, animated: true }
      );
    }
  }, [risultati]);

  if (caricamento) {
    return (
      <View style={styles.centrato}>
        <ActivityIndicator size="large" />
      </View>
    );
  }

  if (errore) {
    return (
      <View style={styles.centrato}>
        <Text>{errore}</Text>
      </View>
    );
  }

  if (risultati.length === 0) {
    return (
      <View style={styles.centrato}>
        <Text>Nessun campo trovato per "{query}"</Text>
      </View>
    );
  }

  return (
    <MapView
      ref={mapRef}
      style={styles.mappa}
      initialRegion={{
        latitude: risultati[0].lat,
        longitude: risultati[0].lng,
        latitudeDelta: 0.05,
        longitudeDelta: 0.05,
      }}
    >
      {risultati.map((s) => (
        <Marker key={s.id} coordinate={{ latitude: s.lat, longitude: s.lng }}>
          <Callout>
            <View style={styles.callout}>
              <Text style={styles.calloutTitolo}>
                {s.siglaSocieta} {s.nomeSocieta}
              </Text>
              <Text>{s.nomeImpianto}</Text>
              <Text>
                {s.indirizzoImpianto}, {s.localitaImpianto} ({s.provinciaImpianto})
              </Text>
            </View>
          </Callout>
        </Marker>
      ))}
    </MapView>
  );
}

const styles = StyleSheet.create({
  mappa: {
    flex: 1,
  },
  centrato: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: 24,
  },
  callout: {
    maxWidth: 220,
  },
  calloutTitolo: {
    fontWeight: "600",
    marginBottom: 4,
  },
});
