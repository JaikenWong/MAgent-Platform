# syntax=docker/dockerfile:1.7
FROM node:22-alpine AS build
WORKDIR /app
ARG VITE_API_BASE_URL=/api/v1
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund
COPY . .
RUN npm run build

FROM nginx:alpine AS run
COPY --from=build /app/dist /usr/share/nginx/html
# SPA 路由 fallback + API 反代到 backend
RUN printf 'server {\n  listen 80;\n  root /usr/share/nginx/html;\n  index index.html;\n  location / { try_files $uri /index.html; }\n  location /api { proxy_pass http://backend:8080; proxy_set_header Host $host; }\n  location /ws { proxy_pass http://backend:8080; proxy_http_version 1.1; proxy_set_header Upgrade $http_upgrade; proxy_set_header Connection "upgrade"; }\n  location /webhook { proxy_pass http://backend:8080; }\n  location /a2a { proxy_pass http://backend:8080; }\n}\n' > /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]