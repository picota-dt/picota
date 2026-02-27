import json
import sys
import unittest
from pathlib import Path

import torch
from torch.utils.data import DataLoader

ROOT = Path(__file__).resolve().parents[1]
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from kan.DatasetLoader import DatasetLoader
from kan.MetamorphicCatalog import (
    build_solar_plant_active_power_rule_specs,
)
from trainer.metamorphic_evaluation import validate_metamorphic_transforms_on_batch, violation_mask
from kan.MetamorphicLoss import (
    CompositeMetamorphicLoss,
    Equal,
    Greater,
    GreaterOrEqual,
    Lower,
    LowerOrEqual,
    MetamorphicTransform,
    Monotonic,
    Proportional,
    TransformSet,
)
from kan.TimeSeriesDataset import TimeSeriesDataset


class _RadiationMonotonicModel(torch.nn.Module):
    def __init__(self, radiation_feature_index: int):
        super().__init__()
        self.radiation_feature_index = radiation_feature_index

    def forward(self, batch):
        radiation = batch["numerical_t_features"][:, self.radiation_feature_index]
        return (radiation * 0.01).unsqueeze(-1)


class _EchoXModel(torch.nn.Module):
    def forward(self, batch):
        return batch["x"].unsqueeze(-1)


class SolarPlantMetamorphicDatasetTest(unittest.TestCase):
    def setUp(self):
        self.data_dir = ROOT / "data" / "solar_plant"
        self.jsonl_path = self.data_dir / "SolarPlant_generatedActivePower.jsonl"
        self.md_path = self.data_dir / "SolarPlant_generatedActivePower+6.md"

    def test_metadata_header_matches_md(self):
        with self.jsonl_path.open("r", encoding="utf-8") as jsonl_file:
            jsonl_header = json.loads(jsonl_file.readline().strip())
        with self.md_path.open("r", encoding="utf-8") as md_file:
            md_header = json.loads(md_file.readline().strip())

        self.assertEqual(jsonl_header["means"], md_header["means"])
        self.assertEqual(jsonl_header["stds"], md_header["stds"])
        self.assertEqual(jsonl_header["out_min"], md_header["out_min"])
        self.assertEqual(jsonl_header["out_max"], md_header["out_max"])
        self.assertEqual(jsonl_header["lookback_size"], md_header["lookback_size"])
        self.assertEqual(jsonl_header["input_variables"], md_header["input_variables"])

    def test_metamorphic_loss_accepts_solarplant_batch(self):
        loader = DatasetLoader(str(self.jsonl_path))
        raw_items = loader.load()
        dataset = TimeSeriesDataset(raw_items[:8])
        batch = next(iter(DataLoader(dataset, batch_size=4, shuffle=False)))

        numerical_names = loader.get_input_variables()[8:15]
        rule_specs = build_solar_plant_active_power_rule_specs(
            numerical_t_feature_names=numerical_names,
            categorical_t_feature_count=batch["categorical_t_features"].shape[-1] if batch[
                                                                                         "categorical_t_features"].dim() > 1 else 0,
        )
        self.assertGreaterEqual(len(rule_specs), 1)

        radiation_idx = numerical_names.index("Infecar.radiation")
        model = _RadiationMonotonicModel(radiation_feature_index=radiation_idx)
        loss_fn = CompositeMetamorphicLoss.from_rule_specs(
            rule_specs=rule_specs,
            supervised_weight=0.1,
            relation_constraint_weight=1.0,
        )

        pred = model(batch).squeeze()
        total = loss_fn.compute_training_loss(model=model, batch=batch, target=batch["out"], prediction=pred)

        self.assertTrue(torch.isfinite(total))
        self.assertIsNotNone(loss_fn.last_metrics)
        self.assertIn("supervised_loss", loss_fn.last_metrics)
        self.assertIn("relation_constraint_penalty", loss_fn.last_metrics)
        self.assertGreaterEqual(loss_fn.last_metrics["relation_constraint_penalty"], 0.0)

    def test_worst_case_over_T_loss_uses_worst_transform_and_supports_target_transform(self):
        def add_x(delta):
            def _transform(batch):
                batch["x"] = batch["x"] + delta
                return batch

            return _transform

        def shift_target(delta):
            def _target_transform(target, *_):
                return target + delta

            return _target_transform

        transform_set = TransformSet(
            [
                MetamorphicTransform(transform=add_x(1.0), name="plus1_same_target"),
                MetamorphicTransform(
                    transform=add_x(2.0),
                    target_transform=shift_target(2.0),
                    name="plus2_shifted_target",
                ),
            ]
        )
        loss_fn = CompositeMetamorphicLoss(
            supervised_loss=torch.nn.MSELoss(),
            metamorphic_tests=None,
            transform_set=transform_set,
            supervised_weight=1.0,
            worst_case_over_T_weight=1.0,
        )

        model = _EchoXModel()
        batch = {"x": torch.tensor([1.0, 2.0], dtype=torch.float32)}
        target = torch.tensor([1.0, 1.0], dtype=torch.float32)
        pred = model(batch).squeeze()
        total = loss_fn.compute_training_loss(model=model, batch=batch, target=target, prediction=pred)

        # Base MSE = 0.5 ; transformed losses = [2.5, 0.5] ; total = 0.5 + max(2.5, 0.5) = 3.0
        self.assertAlmostEqual(float(total.item()), 3.0, places=6)
        self.assertIsNotNone(loss_fn.last_metrics)
        self.assertEqual(loss_fn.last_metrics["loss_type"], "worst_case_over_T")
        self.assertEqual(loss_fn.last_metrics["worst_transform_name"], "plus1_same_target")
        self.assertAlmostEqual(loss_fn.last_metrics["raw_worst_case_over_T_loss"], 2.5, places=6)

    def test_relation_loss_can_add_target_mapped_supervised_term(self):
        def add_x(delta):
            def _transform(batch):
                batch["x"] = batch["x"] + delta
                return batch

            return _transform

        def shift_target(delta):
            def _target_transform(target, *_):
                return target + delta

            return _target_transform

        model = _EchoXModel()
        batch = {"x": torch.tensor([1.0, 2.0], dtype=torch.float32)}
        target = torch.tensor([1.0, 1.0], dtype=torch.float32)

        # Relation penalty is not the focus here; we validate the additional target-mapped supervised term.
        from kan.MetamorphicCatalog import make_equal_test  # local import to avoid import clutter

        test = make_equal_test(
            transform=add_x(1.0),
            name="x_plus_1",
            target_transform=shift_target(1.0),
        )
        loss_fn = CompositeMetamorphicLoss(
            supervised_loss=torch.nn.MSELoss(),
            metamorphic_tests=[test],
            supervised_weight=1.0,
            relation_constraint_weight=0.0,
            target_mapped_weight=1.0,
        )

        pred = model(batch).squeeze()
        total = loss_fn.compute_training_loss(model=model, batch=batch, target=target, prediction=pred)
        # Base MSE = 0.5 ; transformed pred=x+1 ; transformed target=y+1 => MSE still 0.5 ; total=1.0
        self.assertAlmostEqual(float(total.item()), 1.0, places=6)
        self.assertAlmostEqual(loss_fn.last_metrics["raw_target_mapped_supervised_loss"], 0.5, places=6)

    def test_violation_mask_semantics_for_strict_and_non_strict_relations(self):
        base = torch.tensor([1.0], dtype=torch.float32)
        eps = 0.0

        self.assertTrue(
            violation_mask(Greater(margin=0.0), base, torch.tensor([1.0 + eps]), atol=1e-6, rtol=0.0).item())
        self.assertFalse(violation_mask(Greater(margin=0.0), base, torch.tensor([1.1]), atol=1e-6, rtol=0.0).item())

        self.assertFalse(
            violation_mask(GreaterOrEqual(margin=0.0), base, torch.tensor([1.0]), atol=1e-6, rtol=0.0).item()
        )
        self.assertTrue(
            violation_mask(GreaterOrEqual(margin=0.0), base, torch.tensor([0.9]), atol=1e-6, rtol=0.0).item()
        )

        self.assertTrue(violation_mask(Lower(margin=0.0), base, torch.tensor([1.0]), atol=1e-6, rtol=0.0).item())
        self.assertFalse(violation_mask(Lower(margin=0.0), base, torch.tensor([0.8]), atol=1e-6, rtol=0.0).item())

        self.assertFalse(
            violation_mask(LowerOrEqual(margin=0.0), base, torch.tensor([1.0]), atol=1e-6, rtol=0.0).item()
        )
        self.assertTrue(
            violation_mask(LowerOrEqual(margin=0.0), base, torch.tensor([1.2]), atol=1e-6, rtol=0.0).item()
        )

        self.assertFalse(
            violation_mask(Monotonic(direction="increasing"), base, torch.tensor([1.0]), atol=1e-6, rtol=0.0).item()
        )
        self.assertTrue(
            violation_mask(Monotonic(direction="decreasing"), base, torch.tensor([1.2]), atol=1e-6, rtol=0.0).item()
        )

        self.assertFalse(
            violation_mask(Equal(), base, torch.tensor([1.0]), atol=1e-6, rtol=0.0).item()
        )
        self.assertTrue(
            violation_mask(Proportional(factor=2.0), base, torch.tensor([1.5]), atol=1e-6, rtol=0.0).item()
        )

    def test_solarplant_rule_catalog_classification_and_transform_consistency(self):
        loader = DatasetLoader(str(self.jsonl_path))
        raw_items = loader.load()
        dataset = TimeSeriesDataset(raw_items[:8])
        batch = next(iter(DataLoader(dataset, batch_size=4, shuffle=False)))

        numerical_names = loader.get_input_variables()[8:15]
        categorical_count = batch["categorical_t_features"].shape[-1] if batch[
                                                                             "categorical_t_features"].dim() > 1 else 0
        specs = build_solar_plant_active_power_rule_specs(
            numerical_t_feature_names=numerical_names,
            categorical_t_feature_count=categorical_count,
            include_target_mapped=True,
        )
        self.assertGreaterEqual(len(specs), 2)
        self.assertTrue(any(spec.category.value == "directional_ordinal" for spec in specs))

        assigned = CompositeMetamorphicLoss.from_rule_specs(
            rule_specs=specs,
            supervised_weight=0.0,
            relation_constraint_weight=0.0,
            worst_case_over_T_weight=0.0,
        )
        relation_tests = assigned.assigned_relation_constraints
        over_T_transforms = assigned.assigned_over_T_transform_set
        self.assertEqual(assigned.rule_assignment_summary["assigned_relation_constraints"], 2)
        self.assertEqual(assigned.rule_assignment_summary["assigned_over_T_transforms"], 1)
        self.assertEqual(assigned.rule_assignment_summary["dropped_rule_specs"], 0)

        report = validate_metamorphic_transforms_on_batch(
            batch,
            relation_tests=relation_tests,
            transform_set=over_T_transforms,
        )
        self.assertTrue(report["is_valid"])
        self.assertEqual(report["errors"], [])


if __name__ == "__main__":
    unittest.main()
