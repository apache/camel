/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.camel.component.djl.training;

import java.io.IOException;
import java.nio.file.Paths;

import ai.djl.Model;
import ai.djl.basicdataset.cv.classification.Mnist;
import ai.djl.basicmodelzoo.basic.Mlp;
import ai.djl.engine.Engine;
import ai.djl.metric.Metrics;
import ai.djl.ndarray.types.Shape;
import ai.djl.nn.Block;
import ai.djl.training.DefaultTrainingConfig;
import ai.djl.training.EasyTrain;
import ai.djl.training.Trainer;
import ai.djl.training.dataset.Dataset;
import ai.djl.training.dataset.RandomAccessDataset;
import ai.djl.training.evaluator.Accuracy;
import ai.djl.training.listener.TrainingListener;
import ai.djl.training.loss.Loss;
import ai.djl.training.util.ProgressBar;
import ai.djl.translate.TranslateException;

// Helper to train mnist model for tests
public final class MnistTraining {
    private static final String MODEL_DIR = "src/test/resources/models/mnist";
    private static final String MODEL_NAME = "mlp";

    private MnistTraining() {
        // No-op; won't be called
    }

    public static void main(String[] args) throws IOException, TranslateException {
        // Construct neural network
        Block block = new Mlp(Mnist.IMAGE_HEIGHT * Mnist.IMAGE_WIDTH, Mnist.NUM_CLASSES, new int[] { 128, 64 });

        try (Model model = Model.newInstance(MODEL_NAME)) {
            model.setBlock(block);

            // get training and validation dataset
            RandomAccessDataset trainingSet = prepareDataset(Dataset.Usage.TRAIN, 64, Long.MAX_VALUE);
            RandomAccessDataset validateSet = prepareDataset(Dataset.Usage.TEST, 64, Long.MAX_VALUE);

            final Engine engine = Engine.getInstance();
            // setup training configuration
            DefaultTrainingConfig config = new DefaultTrainingConfig(Loss.softmaxCrossEntropyLoss())
                    .addEvaluator(new Accuracy()).optDevices(engine.getDevices(engine.getGpuCount()))
                    .addTrainingListeners(TrainingListener.Defaults.logging());

            try (Trainer trainer = model.newTrainer(config)) {
                trainer.setMetrics(new Metrics());

                Shape inputShape = new Shape(1, Mnist.IMAGE_HEIGHT * Mnist.IMAGE_WIDTH);

                // initialize trainer with proper input shape
                trainer.initialize(inputShape);
                EasyTrain.fit(trainer, 20, trainingSet, validateSet);
            }
            model.save(Paths.get(MODEL_DIR), MODEL_NAME);
        }
    }

    private static RandomAccessDataset prepareDataset(Dataset.Usage usage, int batchSize, long limit) throws IOException {
        Mnist mnist = Mnist.builder().optUsage(usage).setSampling(batchSize, true).optLimit(limit).build();
        mnist.prepare(new ProgressBar());
        return mnist;
    }

}
