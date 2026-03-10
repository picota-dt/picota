from datetime import datetime, timedelta, timezone

import numpy as np
import pandas as pd


def iso_instants(n: int, start: str, step_minutes: int = 60):
    start_dt = datetime.fromisoformat(start.replace("Z", "+00:00")).astimezone(timezone.utc)
    return [(start_dt + timedelta(minutes=step_minutes * i)).strftime("%Y-%m-%dT%H:%M:%SZ") for i in range(n)]


def season_from_instants(instants):
    dts = [datetime.fromisoformat(s.replace("Z", "+00:00")).astimezone(timezone.utc) for s in instants]
    doy = np.array([dt.timetuple().tm_yday for dt in dts], dtype=float)
    return 1.0 + 0.2 * np.sin(2 * np.pi * doy / 365.0)


def make_split(n: int, seed: int, start_instant: str, spur_mode: str):
    rng = np.random.default_rng(seed)

    instants = iso_instants(n, start=start_instant, step_minutes=60)
    season = season_from_instants(instants)

    area = rng.uniform(0.5, 2.0, size=n)
    rain = rng.uniform(0.0, 1.0, size=n)
    humidity = rng.uniform(0.0, 1.0, size=n)

    a, b = 2.0, 1.0
    eps = rng.normal(0, 0.05, size=n)
    y = area * (a + b * rain) * season + eps

    if spur_mode == "train":
        spur = y + rng.normal(0, 0.02, size=n)  # strong shortcut
    elif spur_mode == "test_shift":
        spur = rng.normal(0, 1.0, size=n) + 3.0  # shifted distribution (OOD after train z-score)
    else:
        raise ValueError("spur_mode must be 'train' or 'test_shift'")

    return pd.DataFrame({
        "instant": instants,
        "area": area,
        "rain": rain,
        "humidity": humidity,
        "spur": spur,
        "y": y
    })


train = make_split(50000, seed=1, start_instant="2024-01-01T00:00:00Z", spur_mode="train")
test = make_split(10000, seed=2, start_instant="2024-06-01T00:00:00Z", spur_mode="test_shift")

train.to_csv("audit_train.tsv", sep="\t", index=False)
test.to_csv("audit_test_shift.tsv", sep="\t", index=False)
print("Wrote audit_train.tsv and audit_test_shift.tsv")
