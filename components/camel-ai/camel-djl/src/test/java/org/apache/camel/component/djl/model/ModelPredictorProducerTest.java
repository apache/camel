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

import ai.djl.MalformedModelException;
import ai.djl.repository.zoo.ModelNotFoundException;
import org.apache.camel.component.djl.model.cv.ZooActionRecognitionPredictor;
import org.apache.camel.component.djl.model.cv.ZooImageClassificationPredictor;
import org.apache.camel.component.djl.model.cv.ZooImageGenerationPredictor;
import org.apache.camel.component.djl.model.cv.ZooInstanceSegmentationPredictor;
import org.apache.camel.component.djl.model.cv.ZooObjectDetectionPredictor;
import org.apache.camel.component.djl.model.cv.ZooPoseEstimationPredictor;
import org.apache.camel.component.djl.model.cv.ZooSemanticSegmentationPredictor;
import org.apache.camel.component.djl.model.nlp.ZooQuestionAnswerPredictor;
import org.apache.camel.component.djl.model.nlp.ZooSentimentAnalysisPredictor;
import org.apache.camel.component.djl.model.nlp.ZooWordEmbeddingPredictor;
import org.apache.camel.component.djl.model.timeseries.ZooForecastingPredictor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.djl.model.ModelPredictorProducer.getZooPredictor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ModelPredictorProducerTest {

    @BeforeAll
    public static void setupDefaultEngine() {
        // Since Apache MXNet is discontinued, prefer PyTorch as the default engine
        System.setProperty("ai.djl.default_engine", "PyTorch");
    }

    @Test
    void testGetZooPredictor() throws ModelNotFoundException, MalformedModelException, IOException {
        // CV
        assertInstanceOf(ZooImageClassificationPredictor.class,
                getZooPredictor("cv/image_classification", "ai.djl.zoo:mlp:0.0.3"));
        assertInstanceOf(ZooObjectDetectionPredictor.class,
                getZooPredictor("cv/object_detection", "ai.djl.zoo:ssd:0.0.2"));
        assertInstanceOf(ZooSemanticSegmentationPredictor.class,
                getZooPredictor("cv/semantic_segmentation", "ai.djl.pytorch:deeplabv3:0.0.1"));
        assertInstanceOf(ZooInstanceSegmentationPredictor.class,
                getZooPredictor("cv/instance_segmentation", "ai.djl.mxnet:mask_rcnn:0.0.1"));
        assertInstanceOf(ZooPoseEstimationPredictor.class,
                getZooPredictor("cv/pose_estimation", "ai.djl.mxnet:simple_pose:0.0.1"));
        assertInstanceOf(ZooActionRecognitionPredictor.class,
                getZooPredictor("cv/action_recognition", "ai.djl.mxnet:action_recognition:0.0.1"));
        // No builtin zoo model available for "cv/word_recognition"
        assertInstanceOf(ZooImageGenerationPredictor.class,
                getZooPredictor("cv/image_generation", "ai.djl.pytorch:biggan-deep:0.0.1"));
        // No builtin zoo model available for "cv/image_enhancement"

        // NLP
        // No builtin zoo model available for "nlp/fill_mask"
        assertInstanceOf(ZooQuestionAnswerPredictor.class,
                getZooPredictor("nlp/question_answer", "ai.djl.pytorch:bertqa:0.0.1"));
        // No builtin zoo model available for "nlp/text_classification"
        assertInstanceOf(ZooSentimentAnalysisPredictor.class,
                getZooPredictor("nlp/sentiment_analysis", "ai.djl.pytorch:distilbert:0.0.1"));
        // No builtin zoo model available for "nlp/token_classification"
        assertInstanceOf(ZooWordEmbeddingPredictor.class,
                getZooPredictor("nlp/word_embedding", "ai.djl.mxnet:glove:0.0.2"));
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
        assertInstanceOf(ZooForecastingPredictor.class,
                getZooPredictor("timeseries/forecasting", "ai.djl.pytorch:deepar:0.0.1"));
    }
}
