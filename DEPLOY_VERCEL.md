# Deploy TrovaCampo su Vercel

## Passi per deployare

### 1. Vai a https://vercel.com e accedi con GitHub

### 2. Clicca "Add New..." → "Project"

### 3. Seleziona `cardinal76/trovaCampo`

### 4. Configurazione:
- **Framework Preset**: Angular
- **Root Directory**: `.` (lascia vuoto)
- **Build Command**: `cd mobile-ionic && npm install && npm run build`
- **Output Directory**: `mobile-ionic/dist/mobile-ionic/browser`

Vercel leggerà automaticamente `vercel.json` e applicherà la configurazione.

### 5. Clicca "Deploy"

Fatto! Avrai un URL come `https://trovacampo-staging.vercel.app`

## Backend

L'app Ionic ha bisogno di un'API. Opzioni:

**A) Localhost (testing locale)**
```bash
cd backend-java
docker compose up -d
./mvnw spring-boot:run
```
Modifica `mobile-ionic/src/environments/environment.prod.ts`:
```typescript
export const environment = {
  production: true,
  apiUrl: 'http://[tuo-ip]:3000'
};
```

**B) Deploy backend su Render (gratuito)**
1. https://render.com
2. "New +" → "Web Service"
3. Collega GitHub, seleziona `cardinal76/trovaCampo`
4. **Build Command**: `cd backend-java && ./mvnw clean package -DskipTests`
5. **Start Command**: `java -jar target/trovacampo-api-0.1.0.jar`
6. **Environment**: `PORT=3000` e `MONGODB_URI=...` (MongoDB Atlas gratuito)

Poi aggiorna l'URL dell'API in `environment.prod.ts` con l'URL di Render.

## Testing

Accedi a https://trovacampo-staging.vercel.app e prova a cercare un campo.
