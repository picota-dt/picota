#!/usr/bin/env bash
set -euo pipefail

# Ejecutar desde la raiz de picota-service
ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

# 1) Build del wheel del modulo
python3.13 -m pip install -U pip setuptools wheel
python3.13 -m pip wheel . -w dist

# 2) Descarga de wheels de dependencias para despliegue
python3.13 -m pip wheel -r requirements.txt -w dist

# 3) Bundle final para copiar al servidor
tar -czf picota-service-bundle-0.1.0.tgz dist requirements.txt pyproject.toml

echo "Bundle generado en: $ROOT_DIR/picota-service-bundle-0.1.0.tgz"
