from torch.utils.data import DataLoader, random_split
import torch
from kan.KAN import KAN
import shap
import numpy as np
import math
from torch.utils.data import random_split
from torch.utils.data import DataLoader


class KanTrainer:

	def __init__(self, inputVariables, outputVariable, batch_size, epochs, device, test_proportion, lr,
				 loss_fn,
				 validation_loss_fn):
		self.inputVariables = list(inputVariables)
		self.outputVariable = outputVariable
		self.batch_size = batch_size
		self.epochs = epochs
		self.device = device
		self.test_proportion = test_proportion
		self.train_proportion = 1 - test_proportion
		self.lr = lr
		self.loss_fn = loss_fn
		self.validation_loss_fn = validation_loss_fn

	def train(self, dataset):
		train_length = int(self.train_proportion * len(dataset))
		test_length = len(dataset) - train_length
		train_dataset, test_dataset = random_split(dataset, [train_length, test_length])
		train_loader = DataLoader(train_dataset, batch_size=self.batch_size, shuffle=True)
		test_loader = DataLoader(test_dataset, batch_size=self.batch_size, shuffle=False)
		architecture = KAN(len(self.inputVariables), 1)
		architecture.to(self.device)
		optimizer = torch.optim.Adam(architecture.parameters(), lr=self.lr)
		last_arch = (architecture, None)
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
			val_loss = self.validate(architecture, test_loader)
			if (last_arch[1] == None or last_arch[1] > val_loss):
				last_arch = (self.copy(architecture), val_loss)

		explainer = shap.GradientExplainer(architecture, torch.stack([item[0] for item in train_dataset], dim=0))
		shap_values = explainer.shap_values(torch.stack([item[0] for item in test_dataset], dim=0))
		shap_importance = np.abs(shap_values).mean(axis=0)
		threshold = 0.05
		features = {self.inputVariables[i]: shap_importance[i] for i in range(len(shap_importance)) if shap_importance[i] > threshold}
		return last_arch[0], math.sqrt(last_arch[1]), features

	def validate(self, architecture, test_loader):
		architecture.eval()
		val_loss = 0.0
		with torch.no_grad():
			for inputs, targets in test_loader:
				inputs, targets = inputs.to(self.device), targets.to(self.device)
				predictions = architecture(inputs)
				loss = self.validation_loss_fn(predictions, targets)
				val_loss += loss.item()
		return val_loss

	def copy(self, architecture):
		cloned = KAN(len(self.inputVariables), 1)
		cloned.load_state_dict(architecture.state_dict())
		return cloned

