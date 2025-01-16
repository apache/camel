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
package org.apache.camel.component.kserve.it;

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.protobuf.ByteString;
import inference.GrpcPredictV2;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kserve.KServeConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KServeEndpointIT extends KServeITSupport {

    private static final String TEST_MODEL = "simple";
    private static final String TEST_MODEL_VERSION = "1";

    @Test
    void testInfer() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ModelInferResponse.class)
                .simple("${body.rawOutputContentsCount").isEqualTo(2);

        var request = createInferRequest();
        template.sendBody("direct:infer", request);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        var response = mock.getReceivedExchanges().get(0).getMessage().getBody(GrpcPredictV2.ModelInferResponse.class);
        assertInferResponse(response);
    }

    @Test
    void testInfer_version() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ModelInferResponse.class)
                .simple("${body.rawOutputContentsCount").isEqualTo(2);

        var request = createInferRequest();
        template.sendBody("direct:infer_version", request);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        var response = mock.getReceivedExchanges().get(0).getMessage().getBody(GrpcPredictV2.ModelInferResponse.class);
        assertInferResponse(response);
    }

    @Test
    void testInfer_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ModelInferResponse.class)
                .simple("${body.rawOutputContentsCount").isEqualTo(2);

        var request = createInferRequest();
        template.send("direct:infer_headers", e -> {
            e.getMessage().setHeader(KServeConstants.MODEL_NAME, TEST_MODEL);
            e.getMessage().setHeader(KServeConstants.MODEL_VERSION, TEST_MODEL_VERSION);
            e.getMessage().setBody(request);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();

        var response = mock.getReceivedExchanges().get(0).getMessage().getBody(GrpcPredictV2.ModelInferResponse.class);
        assertInferResponse(response);
    }

    private GrpcPredictV2.ModelInferRequest createInferRequest() {
        var ints0 = IntStream.range(1, 17).boxed().collect(Collectors.toList());
        var content0 = GrpcPredictV2.InferTensorContents.newBuilder().addAllIntContents(ints0);
        var input0 = GrpcPredictV2.ModelInferRequest.InferInputTensor.newBuilder()
                .setName("INPUT0").setDatatype("INT32").addShape(1).addShape(16)
                .setContents(content0);

        var ints1 = IntStream.range(0, 16).boxed().collect(Collectors.toList());
        var content1 = GrpcPredictV2.InferTensorContents.newBuilder().addAllIntContents(ints1);
        var input1 = GrpcPredictV2.ModelInferRequest.InferInputTensor.newBuilder()
                .setName("INPUT1").setDatatype("INT32").addShape(1).addShape(16)
                .setContents(content1);

        return GrpcPredictV2.ModelInferRequest.newBuilder()
                .addInputs(0, input0).addInputs(1, input1)
                .build();
    }

    private void assertInferResponse(GrpcPredictV2.ModelInferResponse response) {
        var output0 = toList(response.getRawOutputContents(0));
        // output0 = input0 + input1
        assertEquals(List.of(1, 3, 5, 7, 9, 11, 13, 15, 17, 19, 21, 23, 25, 27, 29, 31), output0);
        var output1 = toList(response.getRawOutputContents(1));
        // output1 = input0 - input1
        assertEquals(List.of(1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1), output1);
    }

    private List<Integer> toList(ByteString content) {
        var buffer = content.asReadOnlyByteBuffer().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer();
        var list = new ArrayList<Integer>(buffer.remaining());
        while (buffer.hasRemaining()) {
            list.add(buffer.get());
        }
        return list;
    }

    @Test
    void testModelReady() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ModelReadyResponse.class)
                .simple("${body.ready}").isEqualTo("true");

        template.sendBody("direct:model-ready", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelReady_version() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ModelReadyResponse.class)
                .simple("${body.ready}").isEqualTo("true");

        template.sendBody("direct:model-ready_version", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelReady_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ModelReadyResponse.class)
                .simple("${body.ready}").isEqualTo("true");

        template.send("direct:model-ready_headers", e -> {
            e.getMessage().setHeader(KServeConstants.MODEL_NAME, TEST_MODEL);
            e.getMessage().setHeader(KServeConstants.MODEL_VERSION, TEST_MODEL_VERSION);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelMetadata() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GrpcPredictV2.ModelMetadataResponse.class);
        mock.message(0).body().simple("${body.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.getVersions(0)}").isEqualTo(TEST_MODEL_VERSION);

        template.sendBody("direct:model-metadata", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelMetadata_version() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GrpcPredictV2.ModelMetadataResponse.class);
        mock.message(0).body().simple("${body.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.getVersions(0)}").isEqualTo(TEST_MODEL_VERSION);

        template.sendBody("direct:model-metadata_version", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testModelMetadata_headers() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GrpcPredictV2.ModelMetadataResponse.class);
        mock.message(0).body().simple("${body.name}").isEqualTo(TEST_MODEL);
        mock.message(0).body().simple("${body.getVersions(0)}").isEqualTo(TEST_MODEL_VERSION);

        template.send("direct:model-metadata_headers", e -> {
            e.getMessage().setHeader(KServeConstants.MODEL_NAME, TEST_MODEL);
            e.getMessage().setHeader(KServeConstants.MODEL_VERSION, TEST_MODEL_VERSION);
        });

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testServerReady() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ServerReadyResponse.class)
                .simple("${body.ready}").isEqualTo("true");

        template.sendBody("direct:server-ready", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testServerLive() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived()
                .body(GrpcPredictV2.ServerLiveResponse.class)
                .simple("${body.live}").isEqualTo("true");

        template.sendBody("direct:server-live", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Test
    void testServerMetadata() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().body(GrpcPredictV2.ServerMetadataResponse.class);
        // IT uses Triton Inference Server
        mock.message(0).body().simple("${body.name}").isEqualTo("triton");
        mock.message(0).body().simple("${body.version}").isNotNull();
        mock.message(0).body().simple("${body.extensionsCount}").isNotEqualTo(0);

        template.sendBody("direct:server-metadata", null);

        mock.await(1, TimeUnit.SECONDS);
        mock.assertIsSatisfied();
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:infer")
                        .toF("kserve:infer?modelName=%s", TEST_MODEL)
                        .to("mock:result");
                from("direct:infer_version")
                        .toF("kserve:infer?modelName=%s&modelVersion=%s", TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:infer_headers")
                        .to("kserve:infer")
                        .to("mock:result");
                from("direct:model-ready")
                        .toF("kserve:model/ready?modelName=%s", TEST_MODEL)
                        .to("mock:result");
                from("direct:model-ready_version")
                        .toF("kserve:model/ready?modelName=%s&modelVersion=%s", TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:model-ready_headers")
                        .to("kserve:model/ready")
                        .to("mock:result");
                from("direct:model-metadata")
                        .toF("kserve:model/metadata?modelName=%s", TEST_MODEL)
                        .to("mock:result");
                from("direct:model-metadata_version")
                        .toF("kserve:model/metadata?modelName=%s&modelVersion=%s", TEST_MODEL, TEST_MODEL_VERSION)
                        .to("mock:result");
                from("direct:model-metadata_headers")
                        .to("kserve:model/metadata")
                        .to("mock:result");
                from("direct:server-ready")
                        .toF("kserve:server/ready")
                        .to("mock:result");
                from("direct:server-live")
                        .toF("kserve:server/live")
                        .to("mock:result");
                from("direct:server-metadata")
                        .toF("kserve:server/metadata")
                        .to("mock:result");
            }
        };
    }
}
