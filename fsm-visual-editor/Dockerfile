# Static FSM editor. Replace /usr/share/nginx/html/config.js to set the backend URL or request function.
FROM node:22-alpine AS build
WORKDIR /editor
COPY package.json package-lock.json ./
RUN npm ci --no-audit --no-fund
COPY index.html tsconfig.json vite.config.ts ./
COPY public public
COPY src src
RUN npm run build

FROM nginx:1.29-alpine
COPY --from=build /editor/dist /usr/share/nginx/html
