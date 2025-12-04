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
import org.apache.camel.component.djl.DJLEndpoint;
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

public final class ModelPredictorProducer {

    private ModelPredictorProducer() {
        // No-op; won't be called
    }

    public static AbstractPredictor getZooPredictor(DJLEndpoint endpoint)
            throws ModelNotFoundException, MalformedModelException, IOException {
        String applicationPath = endpoint.getApplication();

        // CV
        if (IMAGE_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new ZooImageClassificationPredictor(endpoint);
        } else if (OBJECT_DETECTION.getPath().equals(applicationPath)) {
            return new ZooObjectDetectionPredictor(endpoint);
        } else if (SEMANTIC_SEGMENTATION.getPath().equals(applicationPath)) {
            return new ZooSemanticSegmentationPredictor(endpoint);
        } else if (INSTANCE_SEGMENTATION.getPath().equals(applicationPath)) {
            return new ZooInstanceSegmentationPredictor(endpoint);
        } else if (POSE_ESTIMATION.getPath().equals(applicationPath)) {
            return new ZooPoseEstimationPredictor(endpoint);
        } else if (ACTION_RECOGNITION.getPath().equals(applicationPath)) {
            return new ZooActionRecognitionPredictor(endpoint);
        } else if (WORD_RECOGNITION.getPath().equals(applicationPath)) {
            return new ZooWordRecognitionPredictor(endpoint);
        } else if (IMAGE_GENERATION.getPath().equals(applicationPath)) {
            return new ZooImageGenerationPredictor(endpoint);
        } else if (IMAGE_ENHANCEMENT.getPath().equals(applicationPath)) {
            return new ZooImageEnhancementPredictor(endpoint);
        }

        // NLP
        if (FILL_MASK.getPath().equals(applicationPath)) {
            return new ZooFillMaskPredictor(endpoint);
        } else if (QUESTION_ANSWER.getPath().equals(applicationPath)) {
            return new ZooQuestionAnswerPredictor(endpoint);
        } else if (TEXT_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new ZooTextClassificationPredictor(endpoint);
        } else if (SENTIMENT_ANALYSIS.getPath().equals(applicationPath)) {
            return new ZooSentimentAnalysisPredictor(endpoint);
        } else if (TOKEN_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new ZooTokenClassificationPredictor(endpoint);
        } else if (WORD_EMBEDDING.getPath().equals(applicationPath)) {
            return new ZooWordEmbeddingPredictor(endpoint);
        } else if (TEXT_GENERATION.getPath().equals(applicationPath)) {
            return new ZooTextGenerationPredictor(endpoint);
        } else if (MACHINE_TRANSLATION.getPath().equals(applicationPath)) {
            return new ZooMachineTranslationPredictor(endpoint);
        } else if (MULTIPLE_CHOICE.getPath().equals(applicationPath)) {
            return new ZooMultipleChoicePredictor(endpoint);
        } else if (TEXT_EMBEDDING.getPath().equals(applicationPath)) {
            return new ZooTextEmbeddingPredictor(endpoint);
        }

        // Tabular
        if (LINEAR_REGRESSION.getPath().equals(applicationPath)) {
            return new ZooLinearRegressionPredictor(endpoint);
        } else if (SOFTMAX_REGRESSION.getPath().equals(applicationPath)) {
            return new ZooSoftmaxRegressionPredictor(endpoint);
        }

        // Audio
        if (Application.Audio.ANY.getPath().equals(applicationPath)) {
            return new ZooAudioPredictor(endpoint);
        }

        // Time Series
        if (FORECASTING.getPath().equals(applicationPath)) {
            return new ZooForecastingPredictor(endpoint);
        }

        throw new RuntimeCamelException("Application not supported: " + applicationPath);
    }

    public static AbstractPredictor getCustomPredictor(DJLEndpoint endpoint) {
        String applicationPath = endpoint.getApplication();

        // CV
        if (applicationPath.equals(IMAGE_CLASSIFICATION.getPath())) {
            return new CustomCvPredictor<Classifications>(endpoint);
        } else if (applicationPath.equals(OBJECT_DETECTION.getPath())) {
            return new CustomCvPredictor<DetectedObjects>(endpoint);
        } else if (SEMANTIC_SEGMENTATION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<CategoryMask>(endpoint);
        } else if (INSTANCE_SEGMENTATION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<DetectedObjects>(endpoint);
        } else if (POSE_ESTIMATION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<Joints>(endpoint);
        } else if (ACTION_RECOGNITION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<Classifications>(endpoint);
        } else if (WORD_RECOGNITION.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<String>(endpoint);
        } else if (IMAGE_GENERATION.getPath().equals(applicationPath)) {
            return new CustomImageGenerationPredictor(endpoint);
        } else if (IMAGE_ENHANCEMENT.getPath().equals(applicationPath)) {
            return new CustomCvPredictor<Image>(endpoint);
        }

        // NLP
        if (FILL_MASK.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String[]>(endpoint);
        } else if (QUESTION_ANSWER.getPath().equals(applicationPath)) {
            return new CustomQuestionAnswerPredictor(endpoint);
        } else if (TEXT_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<Classifications>(endpoint);
        } else if (SENTIMENT_ANALYSIS.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<Classifications>(endpoint);
        } else if (TOKEN_CLASSIFICATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<Classifications>(endpoint);
        } else if (WORD_EMBEDDING.getPath().equals(applicationPath)) {
            return new CustomWordEmbeddingPredictor(endpoint);
        } else if (TEXT_GENERATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String>(endpoint);
        } else if (MACHINE_TRANSLATION.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String>(endpoint);
        } else if (MULTIPLE_CHOICE.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<String>(endpoint);
        } else if (TEXT_EMBEDDING.getPath().equals(applicationPath)) {
            return new CustomNlpPredictor<NDArray>(endpoint);
        }

        // Tabular
        if (LINEAR_REGRESSION.getPath().equals(applicationPath)) {
            return new CustomTabularPredictor(endpoint);
        } else if (SOFTMAX_REGRESSION.getPath().equals(applicationPath)) {
            return new CustomTabularPredictor(endpoint);
        }

        // Audio
        if (Application.Audio.ANY.getPath().equals(applicationPath)) {
            return new CustomAudioPredictor(endpoint);
        }

        // Time Series
        if (FORECASTING.getPath().equals(applicationPath)) {
            return new CustomForecastingPredictor(endpoint);
        }

        throw new RuntimeCamelException("Application not supported: " + applicationPath);
    }
}
