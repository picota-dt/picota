from torch.utils.data import DataLoader, random_split
import torch
from kan.KAN import KAN
from torch import nn
from torch.utils.data import random_split
from torch.utils.data import DataLoader


class KanTrainer:

    def __init__(self, variables, batch_size, epochs, device, test_proportion, validation_proportion, lr,
                 loss_fn,
                 validation_loss_fn):
        self.variables = variables
        self.batch_size = batch_size
        self.epochs = epochs
        self.device = device
        self.test_proportion = test_proportion
        self.validation_proportion = validation_proportion
        self.train_proportion = 1 - test_proportion - validation_proportion
        self.lr = lr
        self.loss_fn = loss_fn
        self.validation_loss_fn = validation_loss_fn

    def train(self, dataset):
        train_length = int(self.train_proportion * len(dataset))
        test_length = int(self.test_proportion * len(dataset))
        val_length = len(dataset) - train_length - test_length

        train_dataset, test_dataset, val_dataset = random_split(dataset, [train_length, test_length, val_length])
        train_loader = DataLoader(train_dataset, batch_size=self.batch_size, shuffle=True)
        val_loader = DataLoader(val_dataset, batch_size=self.batch_size, shuffle=False)

        architecture = KAN(len(self.variables) - 1, 1)
        architecture.to(self.device)
        optimizer = torch.optim.Adam(architecture.parameters(), lr=self.lr)
        avg_loss = 0
        for epoch in range(self.epochs):
            architecture.train()
            epoch_loss = 0.0
            for inputs, targets in train_loader:
                inputs, targets = inputs.to(self.device), targets.to(self.device)
                predictions = architecture(inputs)
                loss = self.loss_fn(predictions, targets)
                optimizer.zero_grad()
                loss.backward()
                optimizer.step()
                epoch_loss += loss.item()
            avg_loss = epoch_loss / len(train_loader)
        architecture.eval()
        val_loss = 0.0
        with torch.no_grad():
            for inputs, targets in val_loader:
                inputs, targets = inputs.to(self.device), targets.to(self.device)
                predictions = architecture(inputs)
                loss = self.validation_loss_fn(predictions, targets)
                val_loss += loss.item()
        print(f"{avg_loss:.4f}")
        print("")
        return architecture
