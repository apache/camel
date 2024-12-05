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
package org.apache.camel.component.tensorflow.serving;

import org.apache.camel.Converter;
import tensorflow.serving.Classification;
import tensorflow.serving.GetModelMetadata;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.InputOuterClass;
import tensorflow.serving.Predict;
import tensorflow.serving.RegressionOuterClass;

/**
 * Converter methods to convert from / to TensorFlow types.
 */
@Converter(generateLoader = true)
public class TensorFlowServingConverter {

    @Converter
    public static GetModelStatus.GetModelStatusRequest toGetModelStatusRequest(
            GetModelStatus.GetModelStatusRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static GetModelMetadata.GetModelMetadataRequest toGetModelMetadataRequest(
            GetModelMetadata.GetModelMetadataRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static Classification.ClassificationRequest toClassificationRequest(
            Classification.ClassificationRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static Classification.ClassificationRequest toClassificationRequest(
            InputOuterClass.Input input) {
        return Classification.ClassificationRequest.newBuilder().setInput(input).build();
    }

    @Converter
    public static Classification.ClassificationRequest toClassificationRequest(
            InputOuterClass.Input.Builder builder) {
        return Classification.ClassificationRequest.newBuilder().setInput(builder).build();
    }

    @Converter
    public static RegressionOuterClass.RegressionRequest toRegressionRequest(
            RegressionOuterClass.RegressionRequest.Builder builder) {
        return builder.build();
    }

    @Converter
    public static RegressionOuterClass.RegressionRequest toRegressionRequest(
            InputOuterClass.Input input) {
        return RegressionOuterClass.RegressionRequest.newBuilder().setInput(input).build();
    }

    @Converter
    public static RegressionOuterClass.RegressionRequest toRegressionRequest(
            InputOuterClass.Input.Builder builder) {
        return RegressionOuterClass.RegressionRequest.newBuilder().setInput(builder).build();
    }

    @Converter
    public static Predict.PredictRequest toPredictRequest(
            Predict.PredictRequest.Builder builder) {
        return builder.build();
    }
}
