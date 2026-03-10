#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TRAINER_DIR="$(cd "${ROOT_DIR}/../runtime.trainer" && pwd)"
VENV_DIR="${ROOT_DIR}/.venv"
PYTHON_BIN="${PYTHON_BIN:-python3}"

INSTALL_ML_DEPS=1
CPU_TORCH=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --skip-ml-deps)
      INSTALL_ML_DEPS=0
      shift
      ;;
    --cpu-torch)
      CPU_TORCH=1
      shift
      ;;
    *)
      echo "Unknown option: $1" >&2
      echo "Usage: $0 [--skip-ml-deps] [--cpu-torch]" >&2
      exit 1
      ;;
  esac
done

echo "[bootstrap] runtime.test root: ${ROOT_DIR}"
echo "[bootstrap] runtime.trainer root: ${TRAINER_DIR}"

if [[ ! -d "${VENV_DIR}" ]]; then
  echo "[bootstrap] Creating virtualenv at ${VENV_DIR}"
  "${PYTHON_BIN}" -m venv "${VENV_DIR}"
fi

VENV_PY="${VENV_DIR}/bin/python"
VENV_PIP="${VENV_DIR}/bin/pip"

echo "[bootstrap] Upgrading pip/setuptools/wheel"
"${VENV_PY}" -m pip install --upgrade pip setuptools wheel

echo "[bootstrap] Installing shared trainer package (editable)"
"${VENV_PIP}" install -e "${TRAINER_DIR}" --no-deps

if [[ "${INSTALL_ML_DEPS}" -eq 1 ]]; then
  echo "[bootstrap] Installing runtime dependencies (numpy/scipy/shap)"
  "${VENV_PIP}" install numpy scipy shap

  if ! "${VENV_PY}" -c "import torch" >/dev/null 2>&1; then
    echo "[bootstrap] Installing PyTorch"
    if [[ "${CPU_TORCH}" -eq 1 ]]; then
      "${VENV_PIP}" install torch --index-url https://download.pytorch.org/whl/cpu
    else
      "${VENV_PIP}" install torch
    fi
  else
    echo "[bootstrap] PyTorch already installed"
  fi
fi

echo "[bootstrap] Verifying imports"
"${VENV_PY}" - <<'PY'
import importlib

for mod in ["Device", "kan", "kan.MetamorphicLoss", "kan.MetamorphicCatalog", "trainer.metamorphic_evaluation"]:
    importlib.import_module(mod)
print("Shared framework imports OK")
PY

echo "[bootstrap] Done"
echo "[bootstrap] Activate with: source ${VENV_DIR}/bin/activate"
