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
package org.apache.camel.component.kserve;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import inference.GRPCInferenceServiceGrpc;
import inference.GrpcPredictV2;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body the producer sends, against a local gRPC server that records the requests it receives.
 */
class KServeProducerBodyTest extends CamelTestSupport {

    private static final String MODEL = "simple";
    private static final String JSON = "{\"inputs\": [{\"name\": \"INPUT0\", \"data\": [1, 2]}]}";

    private static final BlockingQueue<Object> REQUESTS = new LinkedBlockingQueue<>();
    private static Server server;

    @BeforeAll
    static void startServer() throws IOException {
        server = Grpc.newServerBuilderForPort(0, InsecureServerCredentials.create())
                .addService(new RecordingInferenceService())
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
        context.getComponent("kserve", KServeComponent.class).getConfiguration()
                .setTarget("localhost:" + server.getPort());
        return context;
    }

    static Stream<Arguments> bodiesThatAreNotTheRequest() {
        byte[] bytes = JSON.getBytes(StandardCharsets.UTF_8);
        Map<String, Object> map = Map.of("inputs", List.of(Map.of("name", "INPUT0", "datatype", "INT32")));
        return Stream.of(
                Arguments.of("infer", GrpcPredictV2.ModelInferRequest.class, JSON),
                Arguments.of("infer", GrpcPredictV2.ModelInferRequest.class, bytes),
                Arguments.of("infer", GrpcPredictV2.ModelInferRequest.class, map),
                // the request of another api
                Arguments.of("infer", GrpcPredictV2.ModelInferRequest.class,
                        GrpcPredictV2.ModelReadyRequest.newBuilder().setName(MODEL).build()),
                Arguments.of("model/ready", GrpcPredictV2.ModelReadyRequest.class, MODEL),
                Arguments.of("model/ready", GrpcPredictV2.ModelReadyRequest.class, map),
                Arguments.of("model/metadata", GrpcPredictV2.ModelMetadataRequest.class, MODEL),
                Arguments.of("model/metadata", GrpcPredictV2.ModelMetadataRequest.class, bytes));
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
    void inferSendsTheRequestOfTheBody() {
        GrpcPredictV2.ModelInferRequest request = inferRequest();

        Exchange exchange = send("infer", request);

        assertNull(exchange.getException());
        GrpcPredictV2.ModelInferRequest sent = sent(GrpcPredictV2.ModelInferRequest.class);
        assertEquals(MODEL, sent.getModelName());
        assertEquals(request.getInputsList(), sent.getInputsList());
        assertInstanceOf(GrpcPredictV2.ModelInferResponse.class, exchange.getMessage().getBody());
    }

    @Test
    void inferSendsTheRequestOfABuilderBody() {
        GrpcPredictV2.ModelInferRequest.Builder builder = inferRequest().toBuilder();

        Exchange exchange = send("infer", builder);

        assertNull(exchange.getException());
        assertEquals(builder.getInputsList(), sent(GrpcPredictV2.ModelInferRequest.class).getInputsList());
    }

    @Test
    void inferWithoutBodySendsTheModelOnly() {
        Exchange exchange = send("infer", null);

        assertNull(exchange.getException());
        GrpcPredictV2.ModelInferRequest sent = sent(GrpcPredictV2.ModelInferRequest.class);
        assertEquals(MODEL, sent.getModelName());
        assertEquals(0, sent.getInputsCount());
    }

    @Test
    void modelReadyWithoutBodyAsksForTheModelOfTheOptions() {
        Exchange exchange = send("model/ready", null);

        assertNull(exchange.getException());
        assertEquals(MODEL, sent(GrpcPredictV2.ModelReadyRequest.class).getName());
        assertInstanceOf(GrpcPredictV2.ModelReadyResponse.class, exchange.getMessage().getBody());
    }

    static Stream<Object> emptyBodies() {
        return Stream.of("", " \n", new byte[0]);
    }

    @ParameterizedTest
    @MethodSource("emptyBodies")
    void modelReadyWithAnEmptyBodyAsksForTheModelOfTheOptions(Object body) {
        Exchange exchange = send("model/ready", body);

        assertNull(exchange.getException());
        assertEquals(MODEL, sent(GrpcPredictV2.ModelReadyRequest.class).getName());
    }

    @Test
    void modelReadySendsTheRequestOfTheBody() {
        GrpcPredictV2.ModelReadyRequest request = GrpcPredictV2.ModelReadyRequest.newBuilder()
                .setName("other").setVersion("2").build();

        Exchange exchange = send("model/ready", request);

        assertNull(exchange.getException());
        assertEquals(request, sent(GrpcPredictV2.ModelReadyRequest.class));
    }

    @Test
    void modelMetadataWithoutBodyAsksForTheModelOfTheOptions() {
        Exchange exchange = send("model/metadata", null);

        assertNull(exchange.getException());
        assertEquals(MODEL, sent(GrpcPredictV2.ModelMetadataRequest.class).getName());
        assertInstanceOf(GrpcPredictV2.ModelMetadataResponse.class, exchange.getMessage().getBody());
    }

    @Test
    void modelMetadataSendsTheRequestOfABuilderBody() {
        GrpcPredictV2.ModelMetadataRequest.Builder builder = GrpcPredictV2.ModelMetadataRequest.newBuilder()
                .setVersion("2");

        Exchange exchange = send("model/metadata", builder);

        assertNull(exchange.getException());
        GrpcPredictV2.ModelMetadataRequest sent = sent(GrpcPredictV2.ModelMetadataRequest.class);
        assertEquals(MODEL, sent.getName());
        assertEquals("2", sent.getVersion());
    }

    @Test
    void serverReadyDoesNotUseTheBody() {
        Exchange exchange = send("server/ready", JSON);

        assertNull(exchange.getException());
        assertInstanceOf(GrpcPredictV2.ServerReadyRequest.class, REQUESTS.poll());
        assertInstanceOf(GrpcPredictV2.ServerReadyResponse.class, exchange.getMessage().getBody());
    }

    private Exchange send(String api, Object body) {
        return template.send("kserve:" + api + "?modelName=" + MODEL, e -> e.getMessage().setBody(body));
    }

    private static <T> T sent(Class<T> type) {
        return assertInstanceOf(type, REQUESTS.poll(), "The request the server received");
    }

    private static GrpcPredictV2.ModelInferRequest inferRequest() {
        return GrpcPredictV2.ModelInferRequest.newBuilder()
                .addInputs(GrpcPredictV2.ModelInferRequest.InferInputTensor.newBuilder()
                        .setName("INPUT0").setDatatype("INT32").addShape(1).addShape(2)
                        .setContents(GrpcPredictV2.InferTensorContents.newBuilder()
                                .addIntContents(1)
                                .addIntContents(2)))
                .build();
    }

    private static <T> void reply(Object request, T response, StreamObserver<T> observer) {
        REQUESTS.add(request);
        observer.onNext(response);
        observer.onCompleted();
    }

    private static final class RecordingInferenceService extends GRPCInferenceServiceGrpc.GRPCInferenceServiceImplBase {

        @Override
        public void modelInfer(
                GrpcPredictV2.ModelInferRequest request, StreamObserver<GrpcPredictV2.ModelInferResponse> observer) {
            reply(request, GrpcPredictV2.ModelInferResponse.newBuilder().setModelName(request.getModelName()).build(),
                    observer);
        }

        @Override
        public void modelReady(
                GrpcPredictV2.ModelReadyRequest request, StreamObserver<GrpcPredictV2.ModelReadyResponse> observer) {
            reply(request, GrpcPredictV2.ModelReadyResponse.newBuilder().setReady(true).build(), observer);
        }

        @Override
        public void modelMetadata(
                GrpcPredictV2.ModelMetadataRequest request,
                StreamObserver<GrpcPredictV2.ModelMetadataResponse> observer) {
            GrpcPredictV2.ModelMetadataResponse response = GrpcPredictV2.ModelMetadataResponse.newBuilder()
                    .setName(request.getName())
                    .build();
            reply(request, response, observer);
        }

        @Override
        public void serverReady(
                GrpcPredictV2.ServerReadyRequest request, StreamObserver<GrpcPredictV2.ServerReadyResponse> observer) {
            reply(request, GrpcPredictV2.ServerReadyResponse.newBuilder().setReady(true).build(), observer);
        }
    }
}
