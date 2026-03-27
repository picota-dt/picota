#!/usr/bin/env bash
set -euo pipefail

# Ajusta APP_DIR si despliegas en otra ruta
APP_DIR="${APP_DIR:-/opt/picota-ml-service}"
PYTHON_BIN="${PYTHON_BIN:-python3.13}"

cd "$APP_DIR"

# 1) Crear venv local del servicio
"$PYTHON_BIN" -m venv .venv

# 2) Instalar dependencias dentro del venv
.venv/bin/pip install -U pip setuptools wheel
.venv/bin/pip install --index-url https://download.pytorch.org/whl/cu128 --extra-index-url https://pypi.org/simple "torch==2.7.0+cu128"
.venv/bin/pip install numpy==2.2.5 shap==0.47.2

# 3) Instalar el wheel del modulo
WHEEL_FILE="$(ls -1 dist/picota_runtime_trainer-*.whl | tail -n 1)"
.venv/bin/pip install "$WHEEL_FILE"

# 4) Verificacion rapida de CUDA
.venv/bin/python -c "import torch; print(torch.__version__, torch.version.cuda, torch.cuda.is_available())"

echo "Instalacion completada en venv: $APP_DIR/.venv"
