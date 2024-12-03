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

import java.util.Optional;

import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.Int64Value;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.DefaultProducer;
import tensorflow.serving.Classification;
import tensorflow.serving.GetModelMetadata;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.Model;
import tensorflow.serving.ModelServiceGrpc;
import tensorflow.serving.Predict;
import tensorflow.serving.PredictionServiceGrpc;
import tensorflow.serving.RegressionOuterClass;

public class TensorFlowServingProducer extends DefaultProducer {

    private final String api;
    private final ModelServiceGrpc.ModelServiceBlockingStub modelService;
    private final PredictionServiceGrpc.PredictionServiceBlockingStub predictionService;

    public TensorFlowServingProducer(TensorFlowServingEndpoint endpoint) {
        super(endpoint);
        this.api = endpoint.getApi();
        this.modelService = endpoint.getModelService();
        this.predictionService = endpoint.getPredictionService();
    }

    @Override
    public TensorFlowServingEndpoint getEndpoint() {
        return (TensorFlowServingEndpoint) super.getEndpoint();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        GeneratedMessageV3 response = switch (api) {
            case "model-status" -> modelStatus(exchange);
            case "model-metadata" -> modelMetadata(exchange);
            case "classify" -> classify(exchange);
            case "regress" -> regress(exchange);
            case "predict" -> predict(exchange);
            default -> throw new IllegalArgumentException("Unsupported API: " + api);
        };
        exchange.getMessage().setBody(response);
    }

    private Model.ModelSpec.Builder modelSpec(Exchange exchange) {
        Message message = exchange.getMessage();
        TensorFlowServingConfiguration configuration = getEndpoint().getConfiguration();
        String modelName = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.MODEL_NAME, String.class))
                .orElse(configuration.getModelName());
        Long modelVersion = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.MODEL_VERSION, Long.class))
                .orElse(configuration.getModelVersion());
        String modelVersionLabel = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.MODEL_VERSION_LABEL, String.class))
                .orElse(configuration.getModelVersionLabel());
        String signatureName = Optional
                .ofNullable(message.getHeader(TensorFlowServingConstants.SIGNATURE_NAME, String.class))
                .orElse(configuration.getSignatureName());

        Model.ModelSpec.Builder builder = Model.ModelSpec.newBuilder().setName(modelName);
        if (modelVersion != null) {
            builder.setVersion(Int64Value.of(modelVersion));
        }
        if (modelVersionLabel != null) {
            builder.setVersionLabel(modelVersionLabel);
        }
        if (signatureName != null) {
            builder.setSignatureName(signatureName);
        }
        return builder;
    }

    private GetModelStatus.GetModelStatusResponse modelStatus(Exchange exchange) {
        Message message = exchange.getMessage();
        GetModelStatus.GetModelStatusRequest request = message.getBody(GetModelStatus.GetModelStatusRequest.class);
        GetModelStatus.GetModelStatusRequest.Builder builder = GetModelStatus.GetModelStatusRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return modelService.getModelStatus(builder.build());
    }

    private GetModelMetadata.GetModelMetadataResponse modelMetadata(Exchange exchange) {
        Message message = exchange.getMessage();
        GetModelMetadata.GetModelMetadataRequest request = message.getBody(GetModelMetadata.GetModelMetadataRequest.class);
        GetModelMetadata.GetModelMetadataRequest.Builder builder = GetModelMetadata.GetModelMetadataRequest.newBuilder()
                .setModelSpec(modelSpec(exchange))
                .addMetadataField("signature_def");
        if (request != null) {
            builder.mergeFrom(request);
        }
        return predictionService.getModelMetadata(builder.build());
    }

    private Classification.ClassificationResponse classify(Exchange exchange) {
        Message message = exchange.getMessage();
        Classification.ClassificationRequest request = message.getBody(Classification.ClassificationRequest.class);
        Classification.ClassificationRequest.Builder builder = Classification.ClassificationRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return predictionService.classify(builder.build());
    }

    private RegressionOuterClass.RegressionResponse regress(Exchange exchange) {
        Message message = exchange.getMessage();
        RegressionOuterClass.RegressionRequest request = message.getBody(RegressionOuterClass.RegressionRequest.class);
        RegressionOuterClass.RegressionRequest.Builder builder = RegressionOuterClass.RegressionRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return predictionService.regress(builder.build());
    }

    private Predict.PredictResponse predict(Exchange exchange) {
        Message message = exchange.getMessage();
        Predict.PredictRequest request = message.getBody(Predict.PredictRequest.class);
        Predict.PredictRequest.Builder builder = Predict.PredictRequest.newBuilder()
                .setModelSpec(modelSpec(exchange));
        if (request != null) {
            builder.mergeFrom(request);
        }
        return predictionService.predict(builder.build());
    }
}
