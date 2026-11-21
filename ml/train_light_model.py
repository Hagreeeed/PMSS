"""Train and export the educational light-window classifier on synthetic data."""
from pathlib import Path
import tempfile

import numpy as np
import tensorflow as tf


WINDOW = 20
CLASSES = ("stable", "changing", "shadow")


def windows(seed, count_per_class):
    rng = np.random.default_rng(seed)
    features, labels = [], []
    for label in range(len(CLASSES)):
        for _ in range(count_per_class):
            baseline = rng.uniform(0.25 if label != 2 else 0.35, 0.8)
            row = np.full(WINDOW, baseline)
            if label != 0:
                transition = rng.integers(2, WINDOW - 1)
                if label == 1:
                    delta = rng.uniform(0.025, 0.085) * rng.choice((-1, 1))
                else:
                    delta = -rng.uniform(0.105, 0.26)
                row[transition:] += delta
            row += rng.normal(0, rng.uniform(0.0005, 0.004), size=WINDOW)
            features.append(np.clip(row, 0, 1))
            labels.append(label)
    order = rng.permutation(len(labels))
    return np.asarray(features, dtype=np.float32)[order], np.asarray(labels)[order]


def main():
    tf.keras.utils.set_random_seed(42)
    tf.config.threading.set_intra_op_parallelism_threads(2)
    tf.config.threading.set_inter_op_parallelism_threads(2)
    x_train, y_train = windows(42, 5000)
    x_test, y_test = windows(169, 1000)
    model = tf.keras.Sequential([
        tf.keras.Input(shape=(WINDOW,), name="light_window"),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dense(16, activation="relu"),
        tf.keras.layers.Dense(len(CLASSES), activation="softmax", name="class_probabilities"),
    ])
    model.compile(optimizer="adam", loss="sparse_categorical_crossentropy")
    options = tf.data.Options()
    options.threading.private_threadpool_size = 1
    dataset = tf.data.Dataset.from_tensor_slices((x_train, y_train)).batch(128).with_options(options)
    model.fit(dataset, epochs=32, verbose=0)
    with tempfile.TemporaryDirectory() as directory:
        model.export(directory, verbose=False)
        model_bytes = tf.lite.TFLiteConverter.from_saved_model(directory).convert()

    interpreter = tf.lite.Interpreter(model_content=model_bytes, num_threads=1)
    interpreter.allocate_tensors()
    input_index = interpreter.get_input_details()[0]["index"]
    output_index = interpreter.get_output_details()[0]["index"]

    def predict(row):
        interpreter.set_tensor(input_index, np.asarray([row], dtype=np.float32))
        interpreter.invoke()
        return interpreter.get_tensor(output_index)[0]

    predicted = np.array([predict(row).argmax() for row in x_test])
    matrix = tf.math.confusion_matrix(y_test, predicted, num_classes=len(CLASSES)).numpy()
    print("Synthetic test accuracy:", np.mean(predicted == y_test))
    print("Confusion matrix (rows = true labels):\n", matrix)
    for before, after, expected in [(650, 650, 0), (650, 1700, 1), (1700, 380, 2)]:
        normalized = np.log1p([before] * 10 + [after] * 10) / np.log1p(200000)
        probabilities = predict(normalized)
        print(f"{before} -> {after}: {probabilities}")
        if probabilities.argmax() != expected or probabilities[expected] < 0.8:
            raise ValueError(f"Demonstration example failed: {before} -> {after}")

    path = Path(__file__).resolve().parents[1] / "app/src/main/assets/light_activity.tflite"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(model_bytes)
    print(f"Exported {len(model_bytes)} bytes to {path}")


if __name__ == "__main__":
    main()
