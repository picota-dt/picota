package io.picota.digitaltwin.model;

import io.quassar.picota.DigitalTwin;

public record Inference(DigitalTwin.DigitalSubject subject, String variable, double value) {
}
