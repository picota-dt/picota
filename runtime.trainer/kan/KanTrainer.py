import numpy as np
import torch
from scipy.stats import norm
from torch.utils.data import DataLoader
from torch.utils.data import random_split

from kan.KAN import KAN

try:
    import shap
except Exception:  # pragma: no cover - optional dependency in some environments
    shap = None


class KanTrainer:
    def __init__(self, name, input_variables, output_variable, lookback, means, stds, out_min, out_max, batch_size,
                 epochs, device,
                 test_proportion,
                 lr,
                 loss_fn,
                 validation_loss_fn):
        self.lookback = lookback
        self.name = name
        self.inputVariables = list(input_variables)
        self.outputVariable = output_variable
        self.means = means
        self.stds = stds
        self.out_min = out_min
        self.out_max = out_max
        self.batch_size = batch_size
        self.epochs = epochs
        self.device = device
        self.test_proportion = test_proportion
        self.train_proportion = 1 - test_proportion
        self.criterion = torch.nn.MSELoss()
        self.lr = lr
        self.loss_fn = loss_fn
        self.validation_loss_fn = validation_loss_fn
        self.last_validation_metrics = None

    def train(self, dataset):
        torch.device(self.device)
        train_data, val_data, _test_data = self._split_dataset(dataset)
        train_loader = DataLoader(train_data, batch_size=self.batch_size, shuffle=True)
        val_loader = DataLoader(val_data, batch_size=32, shuffle=False)
        architecture = KAN(len(self.inputVariables), self.lookback, self.means, self.stds, 1)
        architecture.to(self.device)
        optimizer = torch.optim.Adam(architecture.parameters(), lr=self.lr)
        best_arch = (self.copy(architecture), None, 0.0, None)
        train_size = len(train_data)
        for epoch in range(self.epochs):
            architecture.train()
            total_loss = 0.0
            supervised_component_sum = 0.0
            relation_constraint_component_sum = 0.0
            worst_case_over_T_component_sum = 0.0
            target_mapped_component_sum = 0.0
            component_samples = 0
            for batch in train_loader:
                batch = self._move_batch_to_device(batch)
                out = batch['out']
                optimizer.zero_grad()
                pred = architecture(batch).squeeze()
                loss = self.compute_train_loss(architecture, batch, out, pred)
                loss.backward()
                optimizer.step()
                total_loss += loss.item() * out.size(0)
                if self.loss_fn is not None and hasattr(self.loss_fn, 'last_metrics') and self.loss_fn.last_metrics:
                    supervised_component_sum += self.loss_fn.last_metrics.get('supervised_loss', 0.0) * out.size(0)
                    relation_constraint_component_sum += self.loss_fn.last_metrics.get('relation_constraint_penalty',
                                                                                       0.0) * out.size(0)
                    worst_case_over_T_component_sum += self.loss_fn.last_metrics.get('worst_case_over_T_loss',
                                                                                     0.0) * out.size(0)
                    target_mapped_component_sum += self.loss_fn.last_metrics.get('target_mapped_supervised_loss',
                                                                                 0.0) * out.size(0)
                    component_samples += out.size(0)

            val_metrics = self.validate(architecture, val_loader)
            val_loss = val_metrics["mae_model"]
            margin_of_error = val_metrics["margin_model"]
            if best_arch[1] is None or best_arch[1] > val_loss:
                best_arch = (self.copy(architecture), val_loss, margin_of_error, val_metrics)
            if component_samples > 0 and (
                    getattr(self.loss_fn, 'relation_constraints', None) or getattr(self.loss_fn, 'over_T_transform_set',
                                                                                   None)
            ):
                avg_total = total_loss / max(1, train_size)
                avg_supervised = supervised_component_sum / component_samples
                avg_relation_constraint = relation_constraint_component_sum / component_samples
                avg_worst_case_over_T = worst_case_over_T_component_sum / component_samples
                avg_target_mapped = target_mapped_component_sum / component_samples
                print(
                    f"{self.name}\tepoch={epoch + 1}/{self.epochs}\t"
                    f"train_total={avg_total:.6f}\t"
                    f"train_supervised={avg_supervised:.6f}\t"
                    f"train_relation_constraint_penalty={avg_relation_constraint:.6f}\t"
                    f"train_worst_case_over_T_loss={avg_worst_case_over_T:.6f}\t"
                    f"train_target_mapped={avg_target_mapped:.6f}\t"
                    f"val_mae={val_metrics['mae_model']:.6f}\t"
                    f"val_rmse={val_metrics['rmse_model']:.6f}\t"
                    f"val_r2={val_metrics['r2']:.6f}\t"
                    f"val_scale={val_metrics['output_scale']}",
                    flush=True
                )
        best_model, best_val_loss, best_margin_of_error, best_val_metrics = best_arch
        self.last_validation_metrics = best_val_metrics
        if best_val_metrics is not None:
            print(
                f"{self.name}\tbest_val_n={best_val_metrics['n_samples']}\t"
                f"best_val_scale={best_val_metrics['output_scale']}\t"
                f"best_val_mae={best_val_metrics['mae_model']:.6f}\t"
                f"best_val_rmse={best_val_metrics['rmse_model']:.6f}\t"
                f"best_val_r2={best_val_metrics['r2']:.6f}\t"
                f"best_val_mae_raw={best_val_metrics['mae_raw']:.6f}\t"
                f"best_val_rmse_raw={best_val_metrics['rmse_raw']:.6f}",
                flush=True,
            )
        features = self.explain_features(best_model, val_loader, train_loader)
        return best_model, best_val_loss, best_margin_of_error, features

    def explain_features(self, architecture, test_loader, train_loader):
        if shap is None:
            return {}
        train_batches = []
        for batch in train_loader:
            moved = self._move_batch_to_device(batch)
            train_batches.append(architecture.normalize(moved).detach().cpu())
        test_batches = []
        for batch in test_loader:
            moved = self._move_batch_to_device(batch)
            test_batches.append(architecture.normalize(moved).detach().cpu())

        if not train_batches or not test_batches:
            return {}

        train_norm = torch.cat(train_batches, dim=0)
        test_norm = torch.cat(test_batches, dim=0)
        if train_norm.numel() == 0 or test_norm.numel() == 0:
            return {}

        try:
            architecture.kan.cpu()
            explainer = shap.GradientExplainer(architecture.kan, train_norm)
            shap_values = explainer.shap_values(test_norm)
        except Exception:
            architecture.to(self.device)
            return {}
        finally:
            architecture.to(self.device)

        if isinstance(shap_values, list):
            shap_values = shap_values[0]

        shap_importance = np.abs(shap_values).mean(axis=0)
        denom = float(np.sum(shap_importance))
        if denom <= 0:
            return {}
        shap_normalized = shap_importance / denom

        feature_names = self._expanded_feature_names(len(shap_normalized))
        return {
            feature_names[i]: float(shap_normalized[i])
            for i in range(min(len(feature_names), len(shap_normalized)))
        }

    def validate(self, architecture, val_loader):
        architecture.eval()
        losses = []
        preds = []
        targets = []
        with torch.no_grad():
            for batch in val_loader:
                batch = self._move_batch_to_device(batch)
                out = batch['out']
                pred = architecture(batch).squeeze()
                abs_err = torch.abs(pred - out)
                losses.extend(abs_err.cpu().numpy())
                preds.extend(pred.detach().cpu().numpy().reshape(-1))
                targets.extend(out.detach().cpu().numpy().reshape(-1))

        if len(losses) == 0:
            return {
                "output_scale": "unknown",
                "n_samples": 0,
                "mae_model": float("inf"),
                "rmse_model": float("inf"),
                "r2": float("nan"),
                "margin_model": float("inf"),
                "mae_raw": float("inf"),
                "rmse_raw": float("inf"),
                "margin_raw": float("inf"),
            }

        mae = float(np.mean(losses))
        y_pred = np.asarray(preds, dtype=np.float64)
        y_true = np.asarray(targets, dtype=np.float64)
        err = y_pred - y_true
        rmse = float(np.sqrt(np.mean(err ** 2)))
        ss_res = float(np.sum(err ** 2))
        ss_tot = float(np.sum((y_true - np.mean(y_true)) ** 2))
        r2 = float("nan") if ss_tot == 0.0 else float(1.0 - (ss_res / ss_tot))

        std_error = float(np.std(losses, ddof=1)) if len(losses) > 1 else 0.0
        confidence = 0.95
        alpha = 1 - confidence
        z = norm.ppf(1 - alpha / 2)
        margin_model = float(z * std_error)

        output_scale = self._infer_output_scale_from_values(y_true)
        raw_span = self._raw_span_or_one()
        if output_scale == "normalized":
            mae_raw = float(mae * raw_span)
            rmse_raw = float(rmse * raw_span)
            margin_raw = float(margin_model * raw_span)
        else:
            mae_raw = float(mae)
            rmse_raw = float(rmse)
            margin_raw = float(margin_model)

        return {
            "output_scale": output_scale,
            "n_samples": int(y_true.shape[0]),
            "mae_model": mae,
            "rmse_model": rmse,
            "r2": r2,
            "margin_model": margin_model,
            "mae_raw": mae_raw,
            "rmse_raw": rmse_raw,
            "margin_raw": margin_raw,
        }

    def compute_train_loss(self, architecture, batch, out, pred):
        if self.loss_fn is None:
            return self.criterion(pred, out)
        if hasattr(self.loss_fn, 'compute_training_loss'):
            return self.loss_fn.compute_training_loss(model=architecture, batch=batch, target=out, prediction=pred)
        return self.loss_fn(pred, out)

    def copy(self, architecture):
        cloned = KAN(len(self.inputVariables), self.lookback, self.means, self.stds, 1)
        cloned.load_state_dict(architecture.state_dict())
        cloned.to(self.device)
        return cloned

    def _split_dataset(self, dataset):
        n_total = len(dataset)
        if n_total < 3:
            raise ValueError("Dataset must contain at least 3 items")

        test_size = int(round(n_total * float(self.test_proportion)))
        test_size = max(1, test_size)
        if test_size >= n_total:
            test_size = n_total - 1

        train_val_size = n_total - test_size
        val_size = int(round(train_val_size * 0.2))
        val_size = max(1, val_size)
        if val_size >= train_val_size:
            val_size = train_val_size - 1
        train_size = train_val_size - val_size

        generator = torch.Generator()
        generator.manual_seed(torch.initial_seed())

        train_val_data, test_data = random_split(
            dataset,
            [train_val_size, test_size],
            generator=generator,
        )
        train_data, val_data = random_split(
            train_val_data,
            [train_size, val_size],
            generator=generator,
        )
        return train_data, val_data, test_data

    def _move_batch_to_device(self, batch):
        moved = {}
        for key, value in batch.items():
            moved[key] = value.to(self.device) if torch.is_tensor(value) else value
        return moved

    def _expanded_feature_names(self, feature_count):
        base = list(self.inputVariables)
        expanded = list(base)
        for step in range(self.lookback):
            expanded.extend([f"{name}__lookback_{step + 1}" for name in base])
        if len(expanded) < feature_count:
            expanded.extend([f"x_{i}" for i in range(len(expanded), feature_count)])
        return expanded[:feature_count]

    def _raw_span_or_one(self):
        if self.out_min is None or self.out_max is None:
            return 1.0
        candidate = float(self.out_max) - float(self.out_min)
        return candidate if candidate > 0 else 1.0

    def _infer_output_scale_from_values(self, y_true):
        if y_true.size == 0:
            return "unknown"
        observed_min = float(np.min(y_true))
        observed_max = float(np.max(y_true))
        observed_span = max(0.0, observed_max - observed_min)
        if -0.05 <= observed_min <= 1.05 and -0.05 <= observed_max <= 1.05:
            return "normalized"
        raw_span = self._raw_span_or_one()
        if raw_span > 1.5 and abs(observed_span - raw_span) <= 0.10 * max(raw_span, 1e-9):
            return "raw"
        return "raw"
