# Picota

Picota is a **model-driven pipeline** to engineer and deploy **inference-capable Digital Twins (DTs)** whose learned behavioral core is **constrained and monitored** using **Metamorphic Relations (MRs)**. Picota compiles DSML-level specifications into train-time regularization and run-time validation, producing deployable inference services and traceable quality indicators.

- Artifacts repository (case studies, datasets, models): https://github.com/picota-dt/picota-artifacts  
- Recommended tag for reproducibility: **1.3.11**

---

## What Picota does

Picota provides an end-to-end workflow to define digital twins and train inference engines from the **Picota interface**:
1. Define and version the twin model in the **Model** tab.
2. Upload subject datasets in the **Data** tab.
3. Configure the inference engine in the **Inference Engine** tab (KAN/TabNet, epochs, learning rate, window size, batch size).
4. Launch asynchronous training jobs and track status (`queued`, `preparing`, `training`, `evaluating`, `done`, `failed`).
5. Store and expose training results in the UI (for example MAE/R2 and constraint-violation indicators) and use the trained engine for inference and retraining scheduling.

Operationally, Picota backend orchestrates the workflow and delegates training/inference execution to `picota-ml` (PyTorch runtime service).

---

## Metamodel

The Picota **metamodel/DSML** is described in **Quassar**.

- Quassar: https://quassar.io/models/metta/u1Au7USg/

The modeling and training workflow for case studies is executed in the Picota UI.

---

## Requirements

### Core stack
- Java: **21** (backend)
- Python: **3.12** + PyTorch **2.7.0** (`picota-ml`)
- Node.js + npm (frontend build/dev)

### Optional (for faster training)
- NVIDIA GPU runtime
- Docker + NVIDIA Container Toolkit

---

## Install / Build

### 1) Clone and checkout the recommended tag

```bash
git clone https://github.com/picota-dt/picota
cd picota
git checkout 1.3.11
```

### 2) Build frontend assets for backend serving

```bash
cd frontend
npm install
npm run build
```

This writes static assets to `backend/res/webapp`, which are served by the backend.

---

## Run (case studies)

### Recommended workflow (Picota UI)

1) Start the ML runtime service:

```bash
cd picota-ml
pip install -r requirements
python -m picota.picota_service --host 0.0.0.0 --port 8090 --workspace-dir ../temp/training-workspace
```

2) Start backend (configured to call `picota-ml` on port `8090` via `backend/sandbox/application.properties`):

```bash
cd backend
mvn -DskipTests package
java -jar ../out/build/backend/picota.jar sandbox/application.properties
```

3) Open Picota in the browser:

- http://localhost:8080

4) Run the case study from Picota UI:

- Create/open the twin
- Define/update the model in **Model**
- Upload the dataset in **Data**
- Configure algorithm/hyperparameters in **Inference Engine**
- Launch training and review results

See: https://github.com/picota-dt/picota-artifacts

### Outputs
For each case study, Picota generates:

- Training job history and progress in the UI
- Engine quality results (for example MAE/R2 and constraint violations, depending on the target type)
- Persisted training tickets and artifacts in the `picota-ml` workspace:
  - `<workspace>/<case_id>/tickets/<ticket_id>/`
  - For backend-launched runs, `case_id` is typically `default`

### API/debug workflow (optional)

`picota-ml` also exposes direct HTTP APIs for training/inference and case-centric dataset management.  
Reference: `picota-ml/picota/API_CONTRACT.md` and `picota-ml/picota/openapi.yaml`.

---

## GPU runtime notes

To use GPU acceleration, your host should have:
- NVIDIA GPU driver installed
- NVIDIA Container Toolkit installed

Quick check:

```bash
nvidia-smi
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

---

## Project structure (high level)

- `frontend/` — Picota web UI
- `backend/` — Picota backend API + UI serving + orchestration
- `picota-ml/` — Python training/inference runtime service
- `temp/` — local runtime/workspace data (depending on configuration)

---

## Troubleshooting

### Backend starts but training does not launch
- Check `app.training.api.base-url` in `backend/sandbox/application.properties`.
- Ensure `picota-ml` is running and reachable (default in sandbox config: `http://localhost:8090`).

### Training starts but fails during data preparation
- Confirm datasets include required temporal and target columns configured in the model.
- Re-upload the dataset if backend indicates missing stored file.

### Where are training artifacts?
- In `picota-ml` workspace under `<workspace>/<case_id>/tickets/<ticket_id>/`.
- With the command above, workspace is `temp/training-workspace`.

---

## License

Add your license here (or link to `LICENSE`).

---

## Contact / Issues

- Organization: https://github.com/picota-dt  
- Please use GitHub Issues in this repository for software-related questions.

---