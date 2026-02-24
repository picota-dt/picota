# Picota

Picota is a **model-driven pipeline** to engineer and deploy **inference-capable Digital Twins (DTs)** whose learned behavioral core is **constrained and monitored** using **Metamorphic Relations (MRs)**. Picota compiles DSML-level specifications into train-time regularization and run-time validation, producing deployable inference services and traceable quality indicators.

- Artifacts repository (case studies, datasets, models): https://github.com/picota-dt/picota-artifacts  
- Recommended tag for reproducibility: **1.3.11**

---

## What Picota does

Given a Picota DSML model (authored in Quassar) and an input dataset, Picota:
1. Compiles the model into training + validation artifacts (including MR loss terms and MR checks).
2. Trains inference models (neural core implemented in **PyTorch**) for the specified targets.
3. Evaluates and generates an **evaluation report** (PDF) with **MAE/MSE** per predicted variable and feature influence information.
4. Packages deployable inference services (HTTP endpoints) and, optionally, exposes **AAS-aligned** descriptors and quality indicators.

---

## Requirements

Picota is intended to run on a **GPU-enabled runtime**.

### Stack
- Java: **21**
- Python: **3.12**
- PyTorch: **2.7.0**
- Docker Engine + NVIDIA Container Toolkit (for GPU access)

> For consistent reproducibility, Picota is packaged and executed via Docker.

---

## Install / Build

### 1) Clone and checkout the recommended tag

```bash
git clone https://github.com/picota-dt/picota
cd picota
git checkout 1.3.11
```

### 2) Build the Docker image

Picota provides a helper script:

```bash
./docker-build
```

This produces a Docker image containing both:
- the Java orchestration layer, and
- the Python (torch) neural core.

> If your environment requires a specific CUDA runtime, follow the CUDA/base-image instructions in this repository (or in the Dockerfile).

---

## Run (case studies)

Picota is designed to be executed as part of the workflow used in the paper case studies:

1) Go to **https://quassar.io**  
2) Open a case-study model (`model.tara`) from `picota-artifacts`  
3) Deploy it to Picota  
4) Provide the dataset (when applicable) and run training/evaluation

See: https://github.com/picota-dt/picota-artifacts

### Outputs
For each case study, Picota generates:

- `study_cases/<case-name>/report.pdf` — evaluation report (MAE/MSE per predicted variable + feature influence)
- deployable inference endpoints (service runtime)
- runtime quality indicators (including MR violation indicators when defined in the model)

---

## GPU runtime notes

To use GPU inside Docker, your host must have:
- NVIDIA GPU driver installed
- NVIDIA Container Toolkit installed

Quick check:

```bash
nvidia-smi
docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi
```

---

## Project structure (high level)

> Names may vary; adjust if your repository structure differs.

- `docker-build` — build helper
- `Dockerfile` / docker build assets
- Java modules (DT orchestration, packaging, runtime)
- Python modules (torch training/inference, feature influence reporting)
- Integration modules (HTTP endpoints, optional AAS layer)

---

## Troubleshooting

### Docker builds but no GPU is available
- Verify `nvidia-smi` works on the host.
- Verify `docker run --gpus all ...` works (see GPU notes above).
- Ensure Docker is using the NVIDIA runtime.

### Model deploy works but dataset ingestion fails
- Confirm the dataset format matches the variables defined in the DSML model.
- For TSV datasets: confirm there is a header row and tab separators.
- Rows containing blank/empty values may be discarded depending on ingestion rules.

### Where is the evaluation report?
Picota writes the PDF report for each case to:

- `study_cases/<case-name>/report.pdf`

(See the artifacts repository for case-study folders.)

---

## License

Add your license here (or link to `LICENSE`).

---

## Contact / Issues

- Organization: https://github.com/picota-dt  
- Please use GitHub Issues in this repository for software-related questions.

---

## Citation

If you use Picota in academic work, please cite the Picota paper (to be updated once published):

```bibtex
@inproceedings{picota2026,
  title     = {Picota: ...},
  author    = {...},
  booktitle = {MODELS Practice Track},
  year      = {2026}
}
```
