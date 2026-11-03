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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.tensorflow.example.Example;
import org.tensorflow.example.Feature;
import org.tensorflow.example.Features;
import org.tensorflow.example.FloatList;
import org.tensorflow.framework.DataType;
import org.tensorflow.framework.TensorProto;
import tensorflow.serving.Classification;
import tensorflow.serving.GetModelMetadata;
import tensorflow.serving.GetModelStatus;
import tensorflow.serving.InputOuterClass;
import tensorflow.serving.Model;
import tensorflow.serving.ModelServiceGrpc;
import tensorflow.serving.Predict;
import tensorflow.serving.PredictionServiceGrpc;
import tensorflow.serving.RegressionOuterClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body the producer sends, against a local gRPC server that records the requests it receives.
 */
class TensorFlowServingProducerBodyTest extends CamelTestSupport {

    private static final String MODEL = "half_plus_two";
    private static final String JSON = "{\"inputs\": {\"x\": [1.0, 2.0, 5.0]}}";

    private static final BlockingQueue<Object> REQUESTS = new LinkedBlockingQueue<>();
    private static Server server;

    @BeforeAll
    static void startServer() throws IOException {
        server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(new RecordingModelService())
                .addService(new RecordingPredictionService())
                .build()
                .start();
    }

    @AfterAll
    static void stopServer() throws InterruptedException {
        server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }

    @BeforeEach
    void clearRequests() {
        REQUESTS.clear();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getComponent("tensorflow-serving", TensorFlowServingComponent.class).getConfiguration()
                .setTarget("localhost:" + server.getPort());
        return context;
    }

    static Stream<Arguments> bodiesThatAreNotTheRequest() {
        byte[] bytes = JSON.getBytes(StandardCharsets.UTF_8);
        Map<String, Object> map = Map.of("inputs", Map.of("x", List.of(1.0, 2.0, 5.0)));
        return Stream.of(
                Arguments.of("predict", Predict.PredictRequest.class, JSON),
                Arguments.of("predict", Predict.PredictRequest.class, bytes),
                Arguments.of("predict", Predict.PredictRequest.class, map),
                Arguments.of("classify", Classification.ClassificationRequest.class, JSON),
                // the request of another api
                Arguments.of("classify", Classification.ClassificationRequest.class, predictRequest()),
                Arguments.of("regress", RegressionOuterClass.RegressionRequest.class, map),
                Arguments.of("model-status", GetModelStatus.GetModelStatusRequest.class, MODEL),
                Arguments.of("model-metadata", GetModelMetadata.GetModelMetadataRequest.class, bytes));
    }

    @ParameterizedTest
    @MethodSource("bodiesThatAreNotTheRequest")
    void bodyThatIsNotTheRequestFails(String api, Class<?> requestType, Object body) {
        Exchange exchange = send(api, body);

        InvalidPayloadException e = assertInstanceOf(InvalidPayloadException.class, exchange.getException());
        assertEquals(requestType, e.getType());
        assertTrue(e.getMessage().contains(requestType.getCanonicalName()), e.getMessage());
        assertTrue(e.getMessage().contains("the " + api + " api reads"), e.getMessage());
        assertTrue(REQUESTS.isEmpty(), "No request must be sent");
    }

    @Test
    void predictSendsTheRequestOfTheBody() {
        Predict.PredictRequest request = predictRequest();

        Exchange exchange = send("predict", request);

        assertNull(exchange.getException());
        Predict.PredictRequest sent = sent(Predict.PredictRequest.class);
        assertEquals(MODEL, sent.getModelSpec().getName());
        assertEquals(request.getInputsMap(), sent.getInputsMap());
        assertInstanceOf(Predict.PredictResponse.class, exchange.getMessage().getBody());
    }

    @Test
    void predictSendsTheRequestOfABuilderBody() {
        Predict.PredictRequest.Builder builder = predictRequest().toBuilder();

        Exchange exchange = send("predict", builder);

        assertNull(exchange.getException());
        assertEquals(builder.getInputsMap(), sent(Predict.PredictRequest.class).getInputsMap());
    }

    @Test
    void predictWithoutBodySendsTheModelOnly() {
        Exchange exchange = send("predict", null);

        assertNull(exchange.getException());
        Predict.PredictRequest sent = sent(Predict.PredictRequest.class);
        assertEquals(MODEL, sent.getModelSpec().getName());
        assertEquals(0, sent.getInputsCount());
    }

    @Test
    void classifySendsTheInputOfTheBody() {
        InputOuterClass.Input input = input();

        Exchange exchange = send("classify", input);

        assertNull(exchange.getException());
        Classification.ClassificationRequest sent = sent(Classification.ClassificationRequest.class);
        assertEquals(MODEL, sent.getModelSpec().getName());
        assertEquals(input, sent.getInput());
    }

    @Test
    void regressSendsTheInputOfTheBody() {
        InputOuterClass.Input input = input();

        Exchange exchange = send("regress", input);

        assertNull(exchange.getException());
        assertEquals(input, sent(RegressionOuterClass.RegressionRequest.class).getInput());
    }

    @Test
    void modelStatusWithoutBodyAsksForTheModelOfTheOptions() {
        Exchange exchange = send("model-status", null);

        assertNull(exchange.getException());
        assertEquals(MODEL, sent(GetModelStatus.GetModelStatusRequest.class).getModelSpec().getName());
        assertInstanceOf(GetModelStatus.GetModelStatusResponse.class, exchange.getMessage().getBody());
    }

    static Stream<Object> emptyBodies() {
        return Stream.of("", " \n", new byte[0]);
    }

    @ParameterizedTest
    @MethodSource("emptyBodies")
    void modelStatusWithAnEmptyBodyAsksForTheModelOfTheOptions(Object body) {
        Exchange exchange = send("model-status", body);

        assertNull(exchange.getException());
        assertEquals(MODEL, sent(GetModelStatus.GetModelStatusRequest.class).getModelSpec().getName());
    }

    @Test
    void modelMetadataWithoutBodyAsksForSignatureDef() {
        Exchange exchange = send("model-metadata", null);

        assertNull(exchange.getException());
        GetModelMetadata.GetModelMetadataRequest sent = sent(GetModelMetadata.GetModelMetadataRequest.class);
        assertEquals(MODEL, sent.getModelSpec().getName());
        assertEquals(List.of("signature_def"), sent.getMetadataFieldList());
    }

    @Test
    void modelMetadataAsksOnceForSignatureDefWhenTheRequestNamesIt() {
        GetModelMetadata.GetModelMetadataRequest request = GetModelMetadata.GetModelMetadataRequest.newBuilder()
                .addMetadataField("signature_def")
                .build();

        Exchange exchange = send("model-metadata", request);

        assertNull(exchange.getException());
        GetModelMetadata.GetModelMetadataRequest sent = sent(GetModelMetadata.GetModelMetadataRequest.class);
        assertEquals(MODEL, sent.getModelSpec().getName());
        assertEquals(List.of("signature_def"), sent.getMetadataFieldList());
    }

    @Test
    void modelMetadataAsksForSignatureDefWhenTheRequestNamesNoField() {
        GetModelMetadata.GetModelMetadataRequest request = GetModelMetadata.GetModelMetadataRequest.newBuilder()
                .setModelSpec(Model.ModelSpec.newBuilder().setName("other"))
                .build();

        Exchange exchange = send("model-metadata", request);

        assertNull(exchange.getException());
        GetModelMetadata.GetModelMetadataRequest sent = sent(GetModelMetadata.GetModelMetadataRequest.class);
        assertEquals("other", sent.getModelSpec().getName());
        assertEquals(List.of("signature_def"), sent.getMetadataFieldList());
    }

    private Exchange send(String api, Object body) {
        return template.send("tensorflow-serving:" + api + "?modelName=" + MODEL, e -> e.getMessage().setBody(body));
    }

    private static <T> T sent(Class<T> type) {
        return assertInstanceOf(type, REQUESTS.poll(), "The request the server received");
    }

    private static Predict.PredictRequest predictRequest() {
        return Predict.PredictRequest.newBuilder()
                .putInputs("x", TensorProto.newBuilder()
                        .setDtype(DataType.DT_FLOAT)
                        .addFloatVal(1.0f)
                        .addFloatVal(2.0f)
                        .addFloatVal(5.0f)
                        .build())
                .build();
    }

    private static InputOuterClass.Input input() {
        return InputOuterClass.Input.newBuilder()
                .setExampleList(InputOuterClass.ExampleList.newBuilder()
                        .addExamples(Example.newBuilder()
                                .setFeatures(Features.newBuilder()
                                        .putFeature("x", Feature.newBuilder()
                                                .setFloatList(FloatList.newBuilder().addValue(1.0f))
                                                .build()))))
                .build();
    }

    private static <T> void reply(Object request, T response, StreamObserver<T> observer) {
        REQUESTS.add(request);
        observer.onNext(response);
        observer.onCompleted();
    }

    private static final class RecordingModelService extends ModelServiceGrpc.ModelServiceImplBase {

        @Override
        public void getModelStatus(
                GetModelStatus.GetModelStatusRequest request,
                StreamObserver<GetModelStatus.GetModelStatusResponse> observer) {
            reply(request, GetModelStatus.GetModelStatusResponse.getDefaultInstance(), observer);
        }
    }

    private static final class RecordingPredictionService extends PredictionServiceGrpc.PredictionServiceImplBase {

        @Override
        public void classify(
                Classification.ClassificationRequest request,
                StreamObserver<Classification.ClassificationResponse> observer) {
            Classification.ClassificationResponse response = Classification.ClassificationResponse.newBuilder()
                    .setModelSpec(request.getModelSpec())
                    .build();
            reply(request, response, observer);
        }

        @Override
        public void regress(
                RegressionOuterClass.RegressionRequest request,
                StreamObserver<RegressionOuterClass.RegressionResponse> observer) {
            RegressionOuterClass.RegressionResponse response = RegressionOuterClass.RegressionResponse.newBuilder()
                    .setModelSpec(request.getModelSpec())
                    .build();
            reply(request, response, observer);
        }

        @Override
        public void predict(Predict.PredictRequest request, StreamObserver<Predict.PredictResponse> observer) {
            reply(request, Predict.PredictResponse.newBuilder().setModelSpec(request.getModelSpec()).build(), observer);
        }

        @Override
        public void getModelMetadata(
                GetModelMetadata.GetModelMetadataRequest request,
                StreamObserver<GetModelMetadata.GetModelMetadataResponse> observer) {
            reply(request,
                    GetModelMetadata.GetModelMetadataResponse.newBuilder().setModelSpec(request.getModelSpec()).build(),
                    observer);
        }
    }
}
