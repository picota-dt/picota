import math

import numpy as np
import shap
import torch

from kanwithnormalization.TimeSeriesDataset import TimeSeriesDataset
from kanwithnormalization.KAN import KAN
from torch.utils.data import DataLoader
from torch.utils.data import random_split


class KanTrainer:
    def __init__(self, name, input_variables, output_variable, means, stds, batch_size, epochs, device, test_proportion,
                 lr,
                 loss_fn,
                 validation_loss_fn):
        self.name = name
        self.inputVariables = list(input_variables)
        self.outputVariable = output_variable
        self.means = means
        self.stds = stds
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
        train_dataset = TimeSeriesDataset(train_data)
        val_dataset = TimeSeriesDataset(val_data)
        train_loader = DataLoader(train_dataset, batch_size=self.batch_size, shuffle=True)
        val_loader = DataLoader(val_dataset, batch_size=32)
        architecture = KAN(len(self.inputVariables), self.means, self.stds, 1)
        architecture.to(self.device)
        optimizer = torch.optim.Adam(architecture.parameters(), lr=self.lr)
        last_arch = (architecture, None)
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

            val_loss = self.validate(architecture, val_loader)
            if last_arch[1] is None or last_arch[1] > val_loss:
                last_arch = (self.copy(architecture), val_loss)
        #print(f"{source}\t{avg_loss:.4f}\t{avg_val_loss:.4f}")
        # features = self.explain_features(architecture, val_dataset, train_dataset)
        return last_arch[0], math.sqrt(last_arch[1]), {}

    def explain_features(self, architecture, test_dataset, train_dataset):
        explainer = shap.GradientExplainer(architecture, torch.stack([item for item in train_dataset], dim=0))
        shap_values = explainer.shap_values(torch.stack([item[0] for item in test_dataset], dim=0))
        shap_importance = np.abs(shap_values).mean(axis=0)
        threshold = 0.05
        return {self.inputVariables[i]: shap_importance[i] for i in range(len(shap_importance)) if
                shap_importance[i] > threshold}

    def validate(self, architecture, val_loader):
        architecture.eval()
        val_loss = 0.0
        size = 0.0
        with torch.no_grad():
            for batch in val_loader:
                out = batch['out']
                pred = architecture(batch).squeeze()
                loss = torch.nn.L1Loss()(pred, out)
                val_loss += loss.item() * out.size(0)
                size += 1
        return val_loss / size

    def copy(self, architecture):
        cloned = KAN(len(self.inputVariables), self.means, self.stds, 1)  # TODO
        cloned.load_state_dict(architecture.state_dict())
        return cloned
