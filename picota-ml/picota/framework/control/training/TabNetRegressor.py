from __future__ import annotations

import torch
from torch import nn

from picota.framework.control.training.AttentiveTransformer import AttentiveTransformer
from picota.framework.control.training.FeatureTransformer import FeatureTransformer


def _flatten_feature_tensor(tensor: torch.Tensor) -> torch.Tensor:
    if tensor.dim() == 1:
        return tensor.unsqueeze(1)
    return tensor.reshape(tensor.shape[0], -1)


class TabNetRegressor(nn.Module):
    def __init__(
            self,
            *,
            input_dim: int,
            output_dim: int = 1,
            n_d: int = 24,
            n_a: int = 24,
            n_steps: int = 4,
            gamma: float = 1.3,
            dropout: float = 0.05,
            mask_temperature: float = 1.0,
    ):
        super().__init__()
        self.input_dim = int(input_dim)
        self.output_dim = int(output_dim)
        self.n_d = int(n_d)
        self.n_a = int(n_a)
        self.n_steps = int(n_steps)
        self.gamma = float(gamma)
        self.mask_temperature = float(mask_temperature)

        hidden_dim = self.n_d + self.n_a
        self.initial_bn = nn.BatchNorm1d(self.input_dim)
        self.feature_transformers = nn.ModuleList(
            [FeatureTransformer(self.input_dim, hidden_dim, dropout) for _ in range(self.n_steps)]
        )
        self.attentive_transformers = nn.ModuleList(
            [AttentiveTransformer(self.n_a, self.input_dim) for _ in range(max(0, self.n_steps - 1))]
        )
        self.decision_proj = nn.ModuleList([nn.Linear(hidden_dim, self.n_d) for _ in range(self.n_steps)])
        self.attention_proj = nn.ModuleList([nn.Linear(hidden_dim, self.n_a) for _ in range(self.n_steps)])
        self.output_head = nn.Sequential(
            nn.Linear(self.n_d, self.output_dim),
            nn.Sigmoid(),
        )

    @staticmethod
    def batch_to_features(batch: dict[str, torch.Tensor]) -> torch.Tensor:
        parts: list[torch.Tensor] = []
        for key in (
                "t",
                "numerical_t_features",
                "categorical_t_features",
                "lookback_t",
                "numerical_lookback_features",
                "categorical_lookback_features",
        ):
            tensor = batch.get(key)
            if tensor is None:
                continue
            flat = _flatten_feature_tensor(tensor)
            if flat.shape[1] > 0:
                parts.append(flat)
        if not parts:
            raise ValueError("Batch does not contain usable feature tensors for TabNetRegressor")
        return torch.cat(parts, dim=1)

    def forward(self, batch: dict[str, torch.Tensor]) -> torch.Tensor:
        x = self.batch_to_features(batch)
        x = self.initial_bn(x)

        prior = torch.ones_like(x)
        masked_x = x
        aggregated_decision = None
        for step in range(self.n_steps):
            transformed = self.feature_transformers[step](masked_x)
            decision = torch.relu(self.decision_proj[step](transformed))
            attention = torch.relu(self.attention_proj[step](transformed))

            aggregated_decision = decision if aggregated_decision is None else aggregated_decision + decision
            if step < self.n_steps - 1:
                mask = self.attentive_transformers[step](
                    x=attention,
                    prior=prior,
                    temperature=self.mask_temperature,
                )
                prior = prior * (self.gamma - mask)
                masked_x = x * mask

        return self.output_head(aggregated_decision)


__all__ = ["TabNetRegressor"]
