import { NativeStackScreenProps } from "@react-navigation/native-stack";
import { useCallback, useEffect, useLayoutEffect, useMemo, useRef, useState } from "react";
import { ActivityIndicator, FlatList, Keyboard, StyleSheet, Text, View } from "react-native";
import MapView, { Callout, Marker } from "react-native-maps";
import BarraRicerca from "../components/BarraRicerca";
import MessaggioStato from "../components/MessaggioStato";
import VoceCampo from "../components/VoceCampo";
import { indirizzoCompleto, nomeCompleto } from "../formato";
import { useRicercaCampi } from "../hooks/useRicercaCampi";
import { RootStackParamList } from "../navigation";
import { colori, spazi } from "../theme";
import { Societa } from "../types";

type Props = NativeStackScreenProps<RootStackParamList, "Risultati">;

type SocietaGeolocalizzata = Societa & { lat: number; lng: number };

/** Ampiezza della regione mostrata quando c'è un solo campo da inquadrare. */
const DELTA_SINGOLO = 0.02;

function haCoordinate(societa: Societa): societa is SocietaGeolocalizzata {
  return typeof societa.lat === "number" && typeof societa.lng === "number";
}

/**
 * Seconda schermata della Funzione 1 (Ricerca campo): la mappa con i campi
 * trovati (mockup grafica/FunzioneUnoSchermataDue.jpg), con sopra la barra
 * per correggere la ricerca e sotto l'elenco completo dei risultati.
 */
export default function RisultatiScreen({ route, navigation }: Props) {
  const [termine, setTermine] = useState(route.params.query);
  const [testo, setTesto] = useState(route.params.query);
  const mapRef = useRef<MapView>(null);

  const { risultati, stato, riprova } = useRicercaCampi(termine);

  useLayoutEffect(() => {
    navigation.setOptions({ title: termine });
  }, [navigation, termine]);

  const geolocalizzati = useMemo(() => risultati.filter(haCoordinate), [risultati]);

  // Inquadra tutti i campi trovati. Viene richiamata sia quando arrivano
  // nuovi risultati sia quando la mappa è pronta, perché a seconda dei tempi
  // di montaggio l'una o l'altra delle due può arrivare per prima.
  const inquadraCampi = useCallback(() => {
    const mappa = mapRef.current;
    if (!mappa || geolocalizzati.length === 0) {
      return;
    }

    if (geolocalizzati.length === 1) {
      const { lat, lng } = geolocalizzati[0];
      mappa.animateToRegion({
        latitude: lat,
        longitude: lng,
        latitudeDelta: DELTA_SINGOLO,
        longitudeDelta: DELTA_SINGOLO,
      });
      return;
    }

    mappa.fitToCoordinates(
      geolocalizzati.map((s) => ({ latitude: s.lat, longitude: s.lng })),
      { edgePadding: { top: 80, right: 80, bottom: 80, left: 80 }, animated: true }
    );
  }, [geolocalizzati]);

  useEffect(() => {
    inquadraCampi();
  }, [inquadraCampi]);

  function nuovaRicerca() {
    const nuovoTermine = testo.trim();
    if (nuovoTermine.length === 0 || nuovoTermine === termine) {
      return;
    }
    Keyboard.dismiss();
    setTermine(nuovoTermine);
  }

  function apriScheda(societa: Societa) {
    navigation.navigate("Dettaglio", { id: societa.id });
  }

  // La barra di ricerca resta sempre visibile, anche in caso di errore o di
  // ricerca senza risultati, così il termine si può correggere sul posto.
  function contenuto() {
    if (stato === "caricamento") {
      return (
        <View style={styles.centrato}>
          <ActivityIndicator size="large" color={colori.primario} />
        </View>
      );
    }

    if (stato === "errore") {
      return (
        <MessaggioStato
          testo="Impossibile completare la ricerca. Controlla la connessione e riprova."
          azione={{ etichetta: "Riprova", onPress: riprova }}
        />
      );
    }

    if (risultati.length === 0) {
      return (
        <MessaggioStato
          testo={`Nessun campo trovato per "${termine}".`}
          azione={{
            etichetta: "Aggiungi questo campo",
            onPress: () => navigation.navigate("Inserisci"),
          }}
        />
      );
    }

    return (
      <>
        {geolocalizzati.length > 0 && (
          <MapView
            ref={mapRef}
            style={styles.mappa}
            onMapReady={inquadraCampi}
            initialRegion={{
              latitude: geolocalizzati[0].lat,
              longitude: geolocalizzati[0].lng,
              latitudeDelta: DELTA_SINGOLO,
              longitudeDelta: DELTA_SINGOLO,
            }}
          >
            {geolocalizzati.map((societa) => (
              <Marker
                key={societa.id}
                coordinate={{ latitude: societa.lat, longitude: societa.lng }}
              >
                <Callout onPress={() => apriScheda(societa)}>
                  <View style={styles.callout}>
                    <Text style={styles.calloutTitolo}>{nomeCompleto(societa)}</Text>
                    <Text>{societa.nomeImpianto}</Text>
                    <Text>{indirizzoCompleto(societa)}</Text>
                    <Text style={styles.calloutLink}>Vedi scheda società ›</Text>
                  </View>
                </Callout>
              </Marker>
            ))}
          </MapView>
        )}

        <FlatList
          style={styles.lista}
          data={risultati}
          keyExtractor={(societa) => societa.id}
          keyboardShouldPersistTaps="handled"
          ListHeaderComponent={
            <Text style={styles.listaTitolo}>
              {risultati.length === 1 ? "1 campo trovato" : `${risultati.length} campi trovati`}
            </Text>
          }
          renderItem={({ item }) => (
            <VoceCampo societa={item} onPress={() => apriScheda(item)} />
          )}
        />
      </>
    );
  }

  return (
    <View style={styles.container}>
      <BarraRicerca
        valore={testo}
        onChangeValore={setTesto}
        onCerca={nuovaRicerca}
        variante="compatta"
      />
      {contenuto()}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: colori.sfondo,
  },
  mappa: {
    flex: 2,
  },
  lista: {
    flex: 1,
    borderTopWidth: 1,
    borderTopColor: colori.bordoTenue,
  },
  listaTitolo: {
    fontSize: 12,
    color: colori.testoTenue,
    textTransform: "uppercase",
    paddingHorizontal: spazi.m,
    paddingTop: spazi.s,
    paddingBottom: spazi.xs,
  },
  centrato: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingHorizontal: spazi.l,
  },
  callout: {
    maxWidth: 220,
  },
  calloutTitolo: {
    fontWeight: "600",
    marginBottom: spazi.xs,
  },
  calloutLink: {
    color: colori.primario,
    marginTop: 6,
  },
});
