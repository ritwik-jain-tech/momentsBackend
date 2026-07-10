#!/usr/bin/env bash
# Run all three Moments services locally (not GCP).
# Usage: from any directory —
#   bash /path/to/momentsBackend/scripts/run-local-stack.sh
#
# Defaults match typical layout: backend in IdeaProjects, website under Documents.
# Override with MOMENTS_FACE_TAGGING and MOMENTS_WEB.

set -euo pipefail

BACKEND_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FACE_ROOT="${MOMENTS_FACE_TAGGING:-$HOME/IdeaProjects/momentsFaceTagging}"
WEB_ROOT="${MOMENTS_WEB:-$HOME/Documents/moments/website/moments.github.io}"
SA_JSON="${GOOGLE_APPLICATION_CREDENTIALS:-$BACKEND_ROOT/src/main/resources/serviceAccountKey.json}"

echo "Moments local stack — open 3 terminals and run:"
echo ""
echo "--- Terminal 1: Face tagging (port 8081) ---"
echo "cd \"$FACE_ROOT\""
echo "export PORT=8081 ENVIRONMENT=development GOOGLE_CLOUD_PROJECT=\${GOOGLE_CLOUD_PROJECT:-moments-38b77}"
echo "export GOOGLE_APPLICATION_CREDENTIALS=\"$SA_JSON\""
echo "python main.py"
echo ""
echo "--- Terminal 2: Spring Boot (port 8080, local profile) ---"
echo "cd \"$BACKEND_ROOT\""
echo "mvn spring-boot:run -Dspring-boot.run.profiles=local"
echo ""
echo "--- Terminal 3: Vite admin UI ---"
echo "cd \"$WEB_ROOT\""
echo "npm run dev"
echo ""
echo "Then open the URL Vite prints (usually http://127.0.0.1:5173)."
echo "API → http://127.0.0.1:8080  |  Face API → http://127.0.0.1:8081  (see .env.development)"
