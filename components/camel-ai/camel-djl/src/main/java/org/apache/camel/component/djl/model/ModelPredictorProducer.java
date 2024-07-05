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
package org.apache.camel.component.djl.model;

import java.io.IOException;

import ai.djl.Application;
import ai.djl.MalformedModelException;
import ai.djl.modality.Classifications;
import ai.djl.modality.cv.Image;
import ai.djl.modality.cv.output.CategoryMask;
import ai.djl.modality.cv.output.DetectedObjects;
import ai.djl.modality.cv.output.Joints;
import ai.djl.ndarray.NDArray;
import ai.djl.repository.zoo.ModelNotFoundException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.djl.model.audio.CustomAudioPredictor;
import org.apache.camel.component.djl.model.audio.ZooAudioPredictor;
import org.apache.camel.component.djl.model.cv.CustomCvPredictor;
import org.apache.camel.component.djl.model.cv.CustomImageGenerationPredictor;
import org.apache.camel.component.djl.model.cv.ZooActionRecognitionPredictor;
import org.apache.camel.component.djl.model.cv.ZooImageClassificationPredictor;
import org.apache.camel.component.djl.model.cv.ZooImageEnhancementPredictor;
import org.apache.camel.component.djl.model.cv.ZooImageGenerationPredictor;
import org.apache.camel.component.djl.model.cv.ZooInstanceSegmentationPredictor;
import org.apache.camel.component.djl.model.cv.ZooObjectDetectionPredictor;
import org.apache.camel.component.djl.model.cv.ZooPoseEstimationPredictor;
import org.apache.camel.component.djl.model.cv.ZooSemanticSegmentationPredictor;
import org.apache.camel.component.djl.model.cv.ZooWordRecognitionPredictor;
import org.apache.camel.component.djl.model.nlp.CustomNlpPredictor;
import org.apache.camel.component.djl.model.nlp.CustomQuestionAnswerPredictor;
import org.apache.camel.component.djl.model.nlp.CustomWordEmbeddingPredictor;
import org.apache.camel.component.djl.model.nlp.ZooFillMaskPredictor;
import org.apache.camel.component.djl.model.nlp.ZooMachineTranslationPredictor;
import org.apache.camel.component.djl.model.nlp.ZooMultipleChoicePredictor;
import org.apache.camel.component.djl.model.nlp.ZooQuestionAnswerPredictor;
import org.apache.camel.component.djl.model.nlp.ZooSentimentAnalysisPredictor;
import org.apache.camel.component.djl.model.nlp.ZooTextClassificationPredictor;
import org.apache.camel.component.djl.model.nlp.ZooTextEmbeddingPredictor;
import org.apache.camel.component.djl.model.nlp.ZooTextGenerationPredictor;
import org.apache.camel.component.djl.model.nlp.ZooTokenClassificationPredictor;
import org.apache.camel.component.djl.model.nlp.ZooWordEmbeddingPredictor;
import org.apache.camel.component.djl.model.tabular.CustomTabularPredictor;
import org.apache.camel.component.djl.model.tabular.ZooLinearRegressionPredictor;
import org.apache.camel.component.djl.model.tabular.ZooSoftmaxRegressionPredictor;
import org.apache.camel.component.djl.model.timeseries.CustomForecastingPredictor;
import org.apache.camel.component.djl.model.timeseries.ZooForecastingPredictor;

import static ai.djl.Application.CV.ACTION_RECOGNITION;
import static ai.djl.Application.CV.IMAGE_CLASSIFICATION;
import static ai.djl.Application.CV.IMAGE_ENHANCEMENT;
import static ai.djl.Application.CV.IMAGE_GENERATION;
import static ai.djl.Application.CV.INSTANCE_SEGMENTATION;
import static ai.djl.Application.CV.OBJECT_DETECTION;
import static ai.djl.Application.CV.POSE_ESTIMATION;
import static ai.djl.Application.CV.SEMANTIC_SEGMENTATION;
import static ai.djl.Application.CV.WORD_RECOGNITION;
import static ai.djl.Application.NLP.FILL_MASK;
import static ai.djl.Application.NLP.MACHINE_TRANSLATION;
import static ai.djl.Application.NLP.MULTIPLE_CHOICE;
import static ai.djl.Application.NLP.QUESTION_ANSWER;
import static ai.djl.Application.NLP.SENTIMENT_ANALYSIS;
import static ai.djl.Application.NLP.TEXT_CLASSIFICATION;
import static ai.djl.Application.NLP.TEXT_EMBEDDING;
import static ai.djl.Application.NLP.TEXT_GENERATION;
import static ai.djl.Application.NLP.TOKEN_CLASSIFICATION;
import static ai.djl.Application.NLP.WORD_EMBEDDING;
import static ai.djl.Application.Tabular.LINEAR_REGRESSION;
import static ai.djl.Application.Tabular.SOFTMAX_REGRESSION;
import static ai.djl.Application.TimeSeries.FORECASTING;

public final class ModelPredictorProducer {

    private ModelPredictorProducer() {
        // No-op; won't be called
    }

    public static AbstractPredictor getZooPredictor(String applicationPath, String artifactId)
            throws ModelNotFoundException, MalformedModelException, IOException {
        // CV
        if (IMAGE_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new ZooImageClassificationPredictor(artifactId);
        } else if (OBJECT_DETECTION.getPath().equals(applicationPath)) {
            return new ZooObjectDetectionPredictor(artifactId);
        } else if (SEMANTIC_SEGMENTATION.getPath().equals(applicationPath)) {
            return new ZooSemanticSegmentationPredictor(artifactId);
        } else if (INSTANCE_SEGMENTATION.getPath().equals(applicationPath)) {
            return new ZooInstanceSegmentationPredictor(artifactId);
        } else if (POSE_ESTIMATION.getPath().equals(applicationPath)) {
            return new ZooPoseEstimationPredictor(artifactId);
        } else if (ACTION_RECOGNITION.getPath().equals(applicationPath)) {
            return new ZooActionRecognitionPredictor(artifactId);
        } else if (WORD_RECOGNITION.getPath().equals(applicationPath)) {
            return new ZooWordRecognitionPredictor(artifactId);
        } else if (IMAGE_GENERATION.getPath().equals(applicationPath)) {
            return new ZooImageGenerationPredictor(artifactId);
        } else if (IMAGE_ENHANCEMENT.getPath().equals(applicationPath)) {
            return new ZooImageEnhancementPredictor(artifactId);
        }

        // NLP
        if (FILL_MASK.getPath().equals(applicationPath)) {
            return new ZooFillMaskPredictor(artifactId);
        } else if (QUESTION_ANSWER.getPath().equals(applicationPath)) {
            return new ZooQuestionAnswerPredictor(artifactId);
        } else if (TEXT_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new ZooTextClassificationPredictor(artifactId);
        } else if (SENTIMENT_ANALYSIS.getPath().equals(applicationPath)) {
            return new ZooSentimentAnalysisPredictor(artifactId);
        } else if (TOKEN_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new ZooTokenClassificationPredictor(artifactId);
        } else if (WORD_EMBEDDING.getPath().equals(applicationPath)) {
            return new ZooWordEmbeddingPredictor(artifactId);
        } else if (TEXT_GENERATION.getPath().equals(applicationPath)) {
            return new ZooTextGenerationPredictor(artifactId);
        } else if (MACHINE_TRANSLATION.getPath().equals(applicationPath)) {
            return new ZooMachineTranslationPredictor(artifactId);
        } else if (MULTIPLE_CHOICE.getPath().equals(applicationPath)) {
            return new ZooMultipleChoicePredictor(artifactId);
        } else if (TEXT_EMBEDDING.getPath().equals(applicationPath)) {
            return new ZooTextEmbeddingPredictor(artifactId);
        }

        // Tabular
        if (LINEAR_REGRESSION.getPath().equals(applicationPath)) {
            return new ZooLinearRegressionPredictor(artifactId);
        } else if (SOFTMAX_REGRESSION.getPath().equals(applicationPath)) {
            return new ZooSoftmaxRegressionPredictor(artifactId);
        }

        // Audio
        if (Application.Audio.ANY.getPath().equals(applicationPath)) {
            return new ZooAudioPredictor(artifactId);
        }

        // Time Series
        if (FORECASTING.getPath().equals(applicationPath)) {
            return new ZooForecastingPredictor(artifactId);
        }

        throw new RuntimeCamelException("Application not supported: " + applicationPath);
    }

    public static AbstractPredictor getCustomPredictor(String applicationPath, String model, String translator) {
        // CV
        if (applicationPath.equals(IMAGE_CLASSIFICATION.getPath())) {
            return new CustomCvPredictor<Classifications>(model, translator);
        } else if (applicationPath.equals(OBJECT_DETECTION.getPath())) {
            return new CustomCvPredictor<DetectedObjects>(model, translator);
        } else if (SEMANTIC_SEGMENTATION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<CategoryMask>(model, translator);
        } else if (INSTANCE_SEGMENTATION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<DetectedObjects>(model, translator);
        } else if (POSE_ESTIMATION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<Joints>(model, translator);
        } else if (ACTION_RECOGNITION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<Classifications>(model, translator);
        } else if (WORD_RECOGNITION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<String>(model, translator);
        } else if (IMAGE_GENERATION.getPath().equals(applicationPath)) {
            return new CustomImageGenerationPredictor(model, translator);
        } else if (IMAGE_ENHANCEMENT.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<Image>(model, translator);
        }

        // NLP
        if (FILL_MASK.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String[]>(model, translator);
        } else if (QUESTION_ANSWER.getPath().equals(applicationPath)) {
            return new CustomQuestionAnswerPredictor(model, translator);
        } else if (TEXT_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<Classifications>(model, translator);
        } else if (SENTIMENT_ANALYSIS.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<Classifications>(model, translator);
        } else if (TOKEN_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<Classifications>(model, translator);
        } else if (WORD_EMBEDDING.getPath().equals(applicationPath)) {
            return new CustomWordEmbeddingPredictor(model, translator);
        } else if (TEXT_GENERATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String>(model, translator);
        } else if (MACHINE_TRANSLATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String>(model, translator);
        } else if (MULTIPLE_CHOICE.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String>(model, translator);
        } else if (TEXT_EMBEDDING.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<NDArray>(model, translator);
        }

        // Tabular
        if (LINEAR_REGRESSION.getPath().equals(applicationPath)) {
            return new CustomTabularPredictor(model, translator);
        } else if (SOFTMAX_REGRESSION.getPath().equals(applicationPath)) {
            return new CustomTabularPredictor(model, translator);
        }

        // Audio
        if (Application.Audio.ANY.getPath().equals(applicationPath)) {
            return new CustomAudioPredictor(model, translator);
        }

        // Time Series
        if (FORECASTING.getPath().equals(applicationPath)) {
            return new CustomForecastingPredictor(model, translator);
        }

        throw new RuntimeCamelException("Application not supported: " + applicationPath);
    }
}
