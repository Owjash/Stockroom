#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
(cd frontend && npm ci && npm run build)
chmod +x mvnw
exec ./mvnw spring-boot:run -Dspring-boot.run.profiles=postgres,ai
