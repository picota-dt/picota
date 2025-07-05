import numpy as np
import shap
import torch
from scipy.stats import norm
from torch.utils.data import DataLoader
from torch.utils.data import random_split

from kan.KAN import KAN
from kan.TimeSeriesDataset import TimeSeriesDataset


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

    def train(self, dataset):
        torch.device(self.device)
        val_ratio = 0.2
        val_size = int(len(dataset) * val_ratio)
        train_size = len(dataset) - val_size
        train_data, val_data = random_split(dataset, [train_size, val_size])
        train_loader = TimeSeriesDataset(train_data)
        val_loader = TimeSeriesDataset(val_data)
        train_loader = DataLoader(train_loader, batch_size=self.batch_size, shuffle=True)
        val_loader = DataLoader(val_loader, batch_size=32)
        architecture = KAN(len(self.inputVariables), self.lookback, self.means, self.stds, 1)
        architecture.to(self.device)
        optimizer = torch.optim.Adam(architecture.parameters(), lr=self.lr)
        best_arch = (architecture, None)
        for epoch in range(self.epochs):
            architecture.train()
            total_loss = 0.0
            for batch in train_loader:
                out = batch['out']
                optimizer.zero_grad()
                pred = architecture(batch).squeeze()
                loss = self.criterion(pred, out)
                loss.backward()
                optimizer.step()
                total_loss += loss.item() * out.size(0)

            val_loss, margin_of_error = self.validate(architecture, val_loader)
            if best_arch[1] is None or best_arch[1] > val_loss:
                best_arch = (self.copy(architecture), val_loss, margin_of_error)
            # print(f"{self.name}\t{total_loss:.4f}\t{val_loss:.4f}")
        features = self.explain_features(architecture, val_loader, train_loader)
        return best_arch[0], best_arch[1], best_arch[2], features

    def explain_features(self, architecture, test_loader, train_loader):
        explainer = shap.GradientExplainer(architecture.kan,
                                           torch.cat([architecture.normalize(batch) for batch in train_loader], dim=0))
        shap_values = explainer.shap_values(torch.cat([architecture.normalize(batch) for batch in test_loader], dim=0))
        shap_importance = np.abs(shap_values).mean(axis=0)
        shap_normalized = shap_importance / shap_importance.sum()
        return {self.inputVariables[i]: shap_normalized[i] for i in range(len(self.inputVariables))}

    def validate(self, architecture, val_loader):
        architecture.eval()
        losses = []
        with torch.no_grad():
            for batch in val_loader:
                out = batch['out']
                pred = architecture(batch).squeeze()
                abs_err = torch.abs(pred - out)
                losses.extend(abs_err.cpu().numpy())
        mae = np.mean(losses)
        std_error = np.std(losses, ddof=1)
        confidence = 0.95
        alpha = 1 - confidence
        z = norm.ppf(1 - alpha / 2)
        margin_of_error = z * std_error * (self.out_max - self.out_min)
        return mae, margin_of_error

    def copy(self, architecture):
        cloned = KAN(len(self.inputVariables), self.lookback, self.means, self.stds, 1)
        cloned.load_state_dict(architecture.state_dict())
        return cloned
