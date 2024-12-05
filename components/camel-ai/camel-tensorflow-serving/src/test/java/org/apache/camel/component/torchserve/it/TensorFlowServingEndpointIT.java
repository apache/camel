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
package org.apache.camel.component.torchserve.it;

import java.util.concurrent.TimeUnit;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.tensorflow.serving.TensorFlowServingConstants;
import org.junit.jupiter.api.Test;
import org.tensorflow.example.Example;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;
import org.tensorflow.example.FloatList;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import org.tensorflow.framework.TensorShapeProto;
import tensorflow.serving.Classification;
import tensorflow.serving.GetModelMetadata;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.InputOuterClass;
import tensorflow.serving.Predict;
import tensorflow.serving.RegressionOuterClass;

class TensorFlowServingEndpointIT extends TensorFlowServingITSupport {

    private static final String TEST_MODEL = "half_plus_two";
    private static final long TEST_MODEL_VERSION = 123;

    @Test
    void testModelStatus() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GetModelStatus.GetModelStatusResponse.class);
        mock.message(0).body().simple("${body.getModelVersionStatus(0).version}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.getModelVersionStatus(0).state}").isEqualTo("AVAILABLE");

        template.sendBody("direct:model-status", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelStatus_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GetModelStatus.GetModelStatusResponse.class);
        mock.message(0).body().simple("${body.getModelVersionStatus(0).version}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.getModelVersionStatus(0).state}").isEqualTo("AVAILABLE");

        template.send("direct:model-status_headers", e -> {
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_NAME, TEST_MODEL);
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_VERSION, TEST_MODEL_VERSION);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelMetadata() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GetModelMetadata.GetModelMetadataResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.getMetadataOrThrow('signature_def')}").isNotNull();

        template.sendBody("direct:model-metadata", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelMetadata_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GetModelMetadata.GetModelMetadataResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.getMetadataOrThrow('signature_def')}").isNotNull();

        template.send("direct:model-metadata_headers", e -> {
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_NAME, TEST_MODEL);
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_VERSION, TEST_MODEL_VERSION);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testClassify() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Classification.ClassificationResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.result.classificationsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getClassifications(0).classesCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getClassifications(0).getClasses(0).score}").isEqualTo(2.5);

        Classification.ClassificationRequest request = Classification.ClassificationRequest.newBuilder()
                .setInput(InputOuterClass.Input.newBuilder()
                        .setExampleList(InputOuterClass.ExampleList.newBuilder()
                                .addExamples(Example.newBuilder()
                                        .setFeatures(Features.newBuilder()
                                                .putFeature("x", Feature.newBuilder()
                                                        .setFloatList(FloatList.newBuilder().addValue(1.0f))
                                                        .build())))))
                .build();
        template.sendBody("direct:classify", request);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testClassify_convertInput() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Classification.ClassificationResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.result.classificationsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getClassifications(0).classesCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getClassifications(0).getClasses(0).score}").isEqualTo(2.5);

        InputOuterClass.Input input = InputOuterClass.Input.newBuilder()
                .setExampleList(InputOuterClass.ExampleList.newBuilder()
                        .addExamples(Example.newBuilder()
                                .setFeatures(Features.newBuilder()
                                        .putFeature("x", Feature.newBuilder()
                                                .setFloatList(FloatList.newBuilder().addValue(1.0f))
                                                .build()))))
                .build();
        template.sendBody("direct:classify", input);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testClassify_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Classification.ClassificationResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.result.classificationsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getClassifications(0).classesCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getClassifications(0).getClasses(0).score}").isEqualTo(2.5);

        Classification.ClassificationRequest request = Classification.ClassificationRequest.newBuilder()
                .setInput(InputOuterClass.Input.newBuilder()
                        .setExampleList(InputOuterClass.ExampleList.newBuilder()
                                .addExamples(Example.newBuilder()
                                        .setFeatures(Features.newBuilder()
                                                .putFeature("x", Feature.newBuilder()
                                                        .setFloatList(FloatList.newBuilder().addValue(1.0f))
                                                        .build())))))
                .build();
        template.send("direct:classify_headers", e -> {
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_NAME, TEST_MODEL);
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_VERSION, TEST_MODEL_VERSION);
            e.getIn().setHeader(TensorFlowServingConstants.SIGNATURE_NAME, "classify_x_to_y");
            e.getIn().setBody(request);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testRegress() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(RegressionOuterClass.RegressionResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.result.regressionsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getRegressions(0).value}").isEqualTo(2.5);

        RegressionOuterClass.RegressionRequest request = RegressionOuterClass.RegressionRequest.newBuilder()
                .setInput(InputOuterClass.Input.newBuilder()
                        .setExampleList(InputOuterClass.ExampleList.newBuilder()
                                .addExamples(Example.newBuilder()
                                        .setFeatures(Features.newBuilder()
                                                .putFeature("x", Feature.newBuilder()
                                                        .setFloatList(FloatList.newBuilder().addValue(1.0f))
                                                        .build())))))
                .build();
        template.sendBody("direct:regress", request);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testRegress_convertInput() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(RegressionOuterClass.RegressionResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.result.regressionsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getRegressions(0).value}").isEqualTo(2.5);

        InputOuterClass.Input input = InputOuterClass.Input.newBuilder()
                .setExampleList(InputOuterClass.ExampleList.newBuilder()
                        .addExamples(Example.newBuilder()
                                .setFeatures(Features.newBuilder()
                                        .putFeature("x", Feature.newBuilder()
                                                .setFloatList(FloatList.newBuilder().addValue(1.0f))
                                                .build()))))
                .build();
        template.sendBody("direct:regress", input);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testRegress_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(RegressionOuterClass.RegressionResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.result.regressionsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.result.getRegressions(0).value}").isEqualTo(2.5);

        RegressionOuterClass.RegressionRequest request = RegressionOuterClass.RegressionRequest.newBuilder()
                .setInput(InputOuterClass.Input.newBuilder()
                        .setExampleList(InputOuterClass.ExampleList.newBuilder()
                                .addExamples(Example.newBuilder()
                                        .setFeatures(Features.newBuilder()
                                                .putFeature("x", Feature.newBuilder()
                                                        .setFloatList(FloatList.newBuilder().addValue(1.0f))
                                                        .build())))))
                .build();
        template.send("direct:regress_headers", e -> {
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_NAME, TEST_MODEL);
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_VERSION, TEST_MODEL_VERSION);
            e.getIn().setHeader(TensorFlowServingConstants.SIGNATURE_NAME, "regress_x_to_y");
            e.getIn().setBody(request);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testPredict() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Predict.PredictResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.outputsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.getOutputsOrThrow('y')}").isNotNull();
        mock.message(0).body().simple("${body.getOutputsOrThrow('y').getFloatVal(0)}").isEqualTo(2.5);
        mock.message(0).body().simple("${body.getOutputsOrThrow('y').getFloatVal(1)}").isEqualTo(3.0);
        mock.message(0).body().simple("${body.getOutputsOrThrow('y').getFloatVal(2)}").isEqualTo(4.5);

        Predict.PredictRequest request = Predict.PredictRequest.newBuilder()
                .putInputs("x", TensorProto.newBuilder()
                        .setDtype(DataType.DT_FLOAT)
                        .setTensorShape(TensorShapeProto.newBuilder()
                                .addDim(TensorShapeProto.Dim.newBuilder().setSize(3)))
                        .addFloatVal(1.0f)
                        .addFloatVal(2.0f)
                        .addFloatVal(5.0f)
                        .build())
                .build();
        template.sendBody("direct:predict", request);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testPredict_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(Predict.PredictResponse.class);
        mock.message(0).body().simple("${body.modelSpec.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.modelSpec.version.value}").isEqualTo(TEST_MODEL_VERSION);
        mock.message(0).body().simple("${body.outputsCount}").isEqualTo(1);
        mock.message(0).body().simple("${body.getOutputsOrThrow('y')}").isNotNull();
        mock.message(0).body().simple("${body.getOutputsOrThrow('y').getFloatVal(0)}").isEqualTo(2.5);
        mock.message(0).body().simple("${body.getOutputsOrThrow('y').getFloatVal(1)}").isEqualTo(3.0);
        mock.message(0).body().simple("${body.getOutputsOrThrow('y').getFloatVal(2)}").isEqualTo(4.5);

        Predict.PredictRequest request = Predict.PredictRequest.newBuilder()
                .putInputs("x", TensorProto.newBuilder()
                        .setDtype(DataType.DT_FLOAT)
                        .setTensorShape(TensorShapeProto.newBuilder()
                                .addDim(TensorShapeProto.Dim.newBuilder().setSize(3)))
                        .addFloatVal(1.0f)
                        .addFloatVal(2.0f)
                        .addFloatVal(5.0f)
                        .build())
                .build();
        template.send("direct:predict_headers", e -> {
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_NAME, TEST_MODEL);
            e.getIn().setHeader(TensorFlowServingConstants.MODEL_VERSION, TEST_MODEL_VERSION);
            e.getIn().setBody(request);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:model-status")
                        .toF("tensorflow-serving:model-status?modelName=%s&modelVersion=%s",
                                TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:model-status_headers")
                        .to("tensorflow-serving:model-status")
                        .to("mock:result");
                from("direct:model-metadata")
                        .toF("tensorflow-serving:model-metadata?modelName=%s&modelVersion=%s",
                                TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:model-metadata_headers")
                        .to("tensorflow-serving:model-metadata")
                        .to("mock:result");
                from("direct:classify")
                        .toF("tensorflow-serving:classify?modelName=%s&modelVersion=%s&signatureName=%s",
                                TEST_MODEL, TEST_MODEL_VERSION, "classify_x_to_y")
                        .to("mock:result");
                from("direct:classify_headers")
                        .to("tensorflow-serving:classify")
                        .to("mock:result");
                from("direct:regress")
                        .toF("tensorflow-serving:regress?modelName=%s&modelVersion=%s&signatureName=%s",
                                TEST_MODEL, TEST_MODEL_VERSION, "regress_x_to_y")
                        .to("mock:result");
                from("direct:regress_headers")
                        .to("tensorflow-serving:regress")
                        .to("mock:result");
                from("direct:predict")
                        .toF("tensorflow-serving:predict?modelName=%s&modelVersion=%s",
                                TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:predict_headers")
                        .to("tensorflow-serving:predict")
                        .to("mock:result");
            }
        };
    }
}
