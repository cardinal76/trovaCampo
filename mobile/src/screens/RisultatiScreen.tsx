import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useEffect, useMemo, useRef, useState } from "react";
import { ActivityIndicator, FlatList, StyleSheet, Text, View } from "react-native";
import MapView, { Callout, Marker } from "react-native-maps";
import { cercaSocieta } from "../api/societa";
import { RootStackParamList } from "../navigation";
import { Societa } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "Risultati">;

type SocietaGeolocalizzata = Societa & { lat: number; lng: number };

function haCoordinate(s: Societa): s is SocietaGeolocalizzata {
  return typeof s.lat === "number" && typeof s.lng === "number";
}

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

  const geolocalizzati = useMemo(() => risultati.filter(haCoordinate), [risultati]);
  const senzaCoordinate = useMemo(() => risultati.filter((s) => !haCoordinate(s)), [risultati]);

  useEffect(() => {
    if (geolocalizzati.length > 0 && mapRef.current) {
      mapRef.current.fitToCoordinates(
        geolocalizzati.map((s) => ({ latitude: s.lat, longitude: s.lng })),
        { edgePadding: { top: 80, right: 80, bottom: 80, left: 80 }, animated: true }
      );
    }
  }, [geolocalizzati]);

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
    <View style={styles.container}>
      {geolocalizzati.length > 0 && (
        <MapView
          ref={mapRef}
          style={[styles.mappa, senzaCoordinate.length > 0 && styles.mappaRidotta]}
          initialRegion={{
            latitude: geolocalizzati[0].lat,
            longitude: geolocalizzati[0].lng,
            latitudeDelta: 0.05,
            longitudeDelta: 0.05,
          }}
        >
          {geolocalizzati.map((s) => (
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
      )}

      {senzaCoordinate.length > 0 && (
        <FlatList
          style={styles.lista}
          data={senzaCoordinate}
          keyExtractor={(s) => s.id}
          ListHeaderComponent={
            geolocalizzati.length > 0 ? (
              <Text style={styles.listaTitolo}>Non ancora geolocalizzati</Text>
            ) : null
          }
          renderItem={({ item }) => (
            <View style={styles.voceLista}>
              <Text style={styles.voceTitolo}>
                {item.siglaSocieta} {item.nomeSocieta}
              </Text>
              <Text>{item.nomeImpianto}</Text>
              <Text style={styles.voceIndirizzo}>{item.indirizzoImpianto}</Text>
            </View>
          )}
        />
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  mappa: {
    flex: 1,
  },
  mappaRidotta: {
    flex: 2,
  },
  lista: {
    flex: 1,
    borderTopWidth: 1,
    borderTopColor: "#ddd",
  },
  listaTitolo: {
    fontSize: 12,
    color: "#666",
    textTransform: "uppercase",
    paddingHorizontal: 16,
    paddingTop: 12,
  },
  voceLista: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderBottomWidth: 1,
    borderBottomColor: "#eee",
  },
  voceTitolo: {
    fontWeight: "600",
  },
  voceIndirizzo: {
    color: "#666",
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
