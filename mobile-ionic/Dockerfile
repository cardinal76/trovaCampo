# --- build ---------------------------------------------------------------
# Node 22 e non 20: Angular 20 chiede almeno la 20.19 o la 22.12, e le
# immagini 20-alpine di qualche mese fa stanno sotto.
FROM node:22-alpine AS build
WORKDIR /build

COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY . .
# Configurazione "production": usa environment.prod.ts, con l'indirizzo
# pubblico dell'API.
RUN npx ng build --configuration production

# --- runtime -------------------------------------------------------------
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/mobile-ionic/browser /usr/share/nginx/html

EXPOSE 80
