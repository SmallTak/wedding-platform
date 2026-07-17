#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

printf '[wedding-platform] building public website\n'
(
  cd "$ROOT_DIR/wedding-web"
  npm run build
)

printf '[wedding-platform] building console for /console/\n'
(
  cd "$ROOT_DIR/wedding-console"
  VITE_BASE_PATH=/console/ npm run build
)

printf '[wedding-platform] building Spring Boot jar\n'
(
  cd "$ROOT_DIR/wedding-server"
  ./gradlew clean bootJar
)
