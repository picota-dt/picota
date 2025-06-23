package io.picota.digitaltwin.model;

import io.quassar.monentia.picota.DigitalTwin;

public record Inference(DigitalTwin.DigitalSubject subject, String variable, double value) {
}
