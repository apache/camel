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

import static org.apache.camel.component.djl.model.ModelPredictorProducer.getCustomPredictor;
import static org.apache.camel.component.djl.model.ModelPredictorProducer.getZooPredictor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.io.IOException;

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import org.apache.camel.component.djl.DJLEndpoint;
import org.apache.camel.component.djl.model.audio.CustomAudioPredictor;
import org.apache.camel.component.djl.model.cv.CustomCvPredictor;
import org.apache.camel.component.djl.model.cv.CustomImageGenerationPredictor;
import org.apache.camel.component.djl.model.cv.ZooActionRecognitionPredictor;
import org.apache.camel.component.djl.model.cv.ZooImageClassificationPredictor;
import org.apache.camel.component.djl.model.cv.ZooImageGenerationPredictor;
import org.apache.camel.component.djl.model.cv.ZooInstanceSegmentationPredictor;
import org.apache.camel.component.djl.model.cv.ZooObjectDetectionPredictor;
import org.apache.camel.component.djl.model.cv.ZooPoseEstimationPredictor;
import org.apache.camel.component.djl.model.cv.ZooSemanticSegmentationPredictor;
import org.apache.camel.component.djl.model.nlp.CustomNlpPredictor;
import org.apache.camel.component.djl.model.nlp.CustomQuestionAnswerPredictor;
import org.apache.camel.component.djl.model.nlp.CustomWordEmbeddingPredictor;
import org.apache.camel.component.djl.model.nlp.ZooQuestionAnswerPredictor;
import org.apache.camel.component.djl.model.nlp.ZooSentimentAnalysisPredictor;
import org.apache.camel.component.djl.model.nlp.ZooWordEmbeddingPredictor;
import org.apache.camel.component.djl.model.tabular.CustomTabularPredictor;
import org.apache.camel.component.djl.model.timeseries.CustomForecastingPredictor;
import org.apache.camel.component.djl.model.timeseries.ZooForecastingPredictor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ModelPredictorProducerTest {

    @BeforeAll
    public static void setupDefaultEngine() {
        // Since Apache MXNet is discontinued, prefer PyTorch as the default engine
        System.setProperty("ai.djl.default_engine", "PyTorch");
    }

    @Test
    void testGetZooPredictor() throws ModelNotFoundException, MalformedModelException, IOException {
        // CV
        assertInstanceOf(
                ZooImageClassificationPredictor.class,
                getZooPredictor(zooEndpoint("cv/image_classification", "ai.djl.zoo:mlp:0.0.3")));
        assertInstanceOf(
                ZooObjectDetectionPredictor.class,
                getZooPredictor(zooEndpoint("cv/object_detection", "ai.djl.zoo:ssd:0.0.2")));
        assertInstanceOf(
                ZooSemanticSegmentationPredictor.class,
                getZooPredictor(zooEndpoint("cv/semantic_segmentation", "ai.djl.pytorch:deeplabv3:0.0.1")));
        assertInstanceOf(
                ZooInstanceSegmentationPredictor.class,
                getZooPredictor(zooEndpoint("cv/instance_segmentation", "ai.djl.mxnet:mask_rcnn:0.0.1")));
        assertInstanceOf(
                ZooPoseEstimationPredictor.class,
                getZooPredictor(zooEndpoint("cv/pose_estimation", "ai.djl.mxnet:simple_pose:0.0.1")));
        assertInstanceOf(
                ZooActionRecognitionPredictor.class,
                getZooPredictor(zooEndpoint("cv/action_recognition", "ai.djl.mxnet:action_recognition:0.0.1")));
        // No builtin zoo model available for "cv/word_recognition"
        assertInstanceOf(
                ZooImageGenerationPredictor.class,
                getZooPredictor(zooEndpoint("cv/image_generation", "ai.djl.pytorch:biggan-deep:0.0.1")));
        // No builtin zoo model available for "cv/image_enhancement"

        // NLP
        // No builtin zoo model available for "nlp/fill_mask"
        assertInstanceOf(
                ZooQuestionAnswerPredictor.class,
                getZooPredictor(zooEndpoint("nlp/question_answer", "ai.djl.pytorch:bertqa:0.0.1")));
        // No builtin zoo model available for "nlp/text_classification"
        assertInstanceOf(
                ZooSentimentAnalysisPredictor.class,
                getZooPredictor(zooEndpoint("nlp/sentiment_analysis", "ai.djl.pytorch:distilbert:0.0.1")));
        // No builtin zoo model available for "nlp/token_classification"
        assertInstanceOf(
                ZooWordEmbeddingPredictor.class,
                getZooPredictor(zooEndpoint("nlp/word_embedding", "ai.djl.mxnet:glove:0.0.2")));
        // No builtin zoo model available for "nlp/text_generation"
        // No builtin zoo model available for "nlp/machine_translation"
        // No builtin zoo model available for "nlp/multiple_choice"
        // No builtin zoo model available for "nlp/text_embedding"

        // Tabular
        // No builtin zoo model available for "tabular/linear_regression"
        // No builtin zoo model available for "tabular/softmax_regression"

        // Audio
        // No builtin zoo model available for "audio"

        // Time Series
        assertInstanceOf(
                ZooForecastingPredictor.class,
                getZooPredictor(zooEndpoint("timeseries/forecasting", "ai.djl.pytorch:deepar:0.0.1")));
    }

    @Test
    void testGetCustomPredictor() {
        var modelName = "MyModel";
        var translatorName = "MyTranslator";

        // CV
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/image_classification", modelName, translatorName)));
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/object_detection", modelName, translatorName)));
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/semantic_segmentation", modelName, translatorName)));
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/instance_segmentation", modelName, translatorName)));
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/pose_estimation", modelName, translatorName)));
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/action_recognition", modelName, translatorName)));
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/word_recognition", modelName, translatorName)));
        assertInstanceOf(
                CustomImageGenerationPredictor.class,
                getCustomPredictor(customEndpoint("cv/image_generation", modelName, translatorName)));
        assertInstanceOf(
                CustomCvPredictor.class,
                getCustomPredictor(customEndpoint("cv/image_enhancement", modelName, translatorName)));

        // NLP
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/fill_mask", modelName, translatorName)));
        assertInstanceOf(
                CustomQuestionAnswerPredictor.class,
                getCustomPredictor(customEndpoint("nlp/question_answer", modelName, translatorName)));
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/text_classification", modelName, translatorName)));
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/sentiment_analysis", modelName, translatorName)));
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/token_classification", modelName, translatorName)));
        assertInstanceOf(
                CustomWordEmbeddingPredictor.class,
                getCustomPredictor(customEndpoint("nlp/word_embedding", modelName, translatorName)));
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/text_generation", modelName, translatorName)));
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/machine_translation", modelName, translatorName)));
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/multiple_choice", modelName, translatorName)));
        assertInstanceOf(
                CustomNlpPredictor.class,
                getCustomPredictor(customEndpoint("nlp/text_embedding", modelName, translatorName)));

        // Tabular
        assertInstanceOf(
                CustomTabularPredictor.class,
                getCustomPredictor(customEndpoint("tabular/linear_regression", modelName, translatorName)));
        assertInstanceOf(
                CustomTabularPredictor.class,
                getCustomPredictor(customEndpoint("tabular/softmax_regression", modelName, translatorName)));

        // Audio
        assertInstanceOf(
                CustomAudioPredictor.class, getCustomPredictor(customEndpoint("audio", modelName, translatorName)));

        // Time Series
        assertInstanceOf(
                CustomForecastingPredictor.class,
                getCustomPredictor(customEndpoint("timeseries/forecasting", modelName, translatorName)));
    }

    private static DJLEndpoint zooEndpoint(String application, String artifactId) {
        DJLEndpoint endpoint = new DJLEndpoint("djl:" + application, null, application);
        endpoint.setArtifactId(artifactId);
        return endpoint;
    }

    private static DJLEndpoint customEndpoint(String application, String model, String translator) {
        DJLEndpoint endpoint = new DJLEndpoint("djl:" + application, null, application);
        endpoint.setModel(model);
        endpoint.setTranslator(translator);
        return endpoint;
    }
}
