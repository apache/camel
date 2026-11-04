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
package org.apache.camel.component.pinecone;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.pinecone.proto.QueryRequest;
import io.pinecone.proto.QueryResponse;
import io.pinecone.proto.ScoredVector;
import io.pinecone.proto.UpdateRequest;
import io.pinecone.proto.UpdateResponse;
import io.pinecone.proto.UpsertRequest;
import io.pinecone.proto.UpsertResponse;
import io.pinecone.proto.VectorServiceGrpc;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.DataType;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-26216: UPSERT, UPDATE and QUERY take the vector as a List of numbers, such as the List of Double a JSON array
 * unmarshals to, or a float or double array, QUERY and QUERY_BY_ID return 10 matches without the CamelPineconeQueryTopK
 * header, UPSERT without the CamelPineconeIndexId header stores the vector under a random id, and the pinecone:rag data
 * type transformer reads the matches of a QUERY. A fake Pinecone, its control plane on a JDK HttpServer and the data
 * plane of the index on a gRPC server, records the requests of the client, so no Pinecone is needed.
 */
class PineconeVectorDbProducerTest extends CamelTestSupport {

    private static final List<Float> VECTOR = List.of(0.1f, 0.2f, 0.3f);

    // the client keeps a connection per index name for the JVM, so each test has its own index
    private final String indexName = "camel-" + UUID.randomUUID();
    private final List<Object> requests = new CopyOnWriteArrayList<>();
    private HttpServer controlPlane;
    private Server dataPlane;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        dataPlane = ServerBuilder.forPort(0).addService(new VectorServiceGrpc.VectorServiceImplBase() {
            @Override
            public void upsert(UpsertRequest request, StreamObserver<UpsertResponse> reply) {
                requests.add(request);
                reply.onNext(UpsertResponse.newBuilder().setUpsertedCount(request.getVectorsCount()).build());
                reply.onCompleted();
            }

            @Override
            public void update(UpdateRequest request, StreamObserver<UpdateResponse> reply) {
                requests.add(request);
                reply.onNext(UpdateResponse.getDefaultInstance());
                reply.onCompleted();
            }

            @Override
            public void query(QueryRequest request, StreamObserver<QueryResponse> reply) {
                requests.add(request);
                Struct metadata = Struct.newBuilder()
                        .putFields("text_segment", Value.newBuilder().setStringValue("hello").build())
                        .build();
                reply.onNext(QueryResponse.newBuilder()
                        .addMatches(ScoredVector.newBuilder().setId("vector-1").setScore(0.9f).setMetadata(metadata))
                        .build());
                reply.onCompleted();
            }
        }).build().start();
        controlPlane = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        controlPlane.createContext("/indexes/", this::describeIndex);
        controlPlane.start();

        CamelContext context = super.createCamelContext();
        PineconeVectorDbConfiguration configuration
                = context.getComponent(PineconeVectorDb.SCHEME, PineconeVectorDbComponent.class).getConfiguration();
        configuration.setToken("test");
        configuration.setHost("http://localhost:" + controlPlane.getAddress().getPort());
        configuration.setTls(false);
        configuration.setIndexName(indexName);
        return context;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:rag")
                        .to("pinecone:rag?action=QUERY")
                        .transformDataType(new DataType("pinecone:rag"));
            }
        };
    }

    @AfterEach
    void stopFakePinecone() {
        controlPlane.stop(0);
        dataPlane.shutdownNow();
    }

    static Stream<Named<Object>> vectorBodies() {
        return Stream.of(
                Named.of("List of Float", List.of(0.1f, 0.2f, 0.3f)),
                Named.of("List of Double", List.of(0.1d, 0.2d, 0.3d)),
                Named.of("List of BigDecimal", List.of(new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("0.3"))),
                Named.of("float[]", new float[] { 0.1f, 0.2f, 0.3f }),
                Named.of("double[]", new double[] { 0.1d, 0.2d, 0.3d }),
                Named.of("Double[]", new Double[] { 0.1d, 0.2d, 0.3d }));
    }

    @ParameterizedTest
    @MethodSource("vectorBodies")
    void upsertTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("pinecone:test?action=UPSERT")
                .withHeader(PineconeVectorDbHeaders.INDEX_ID, "vector-1")
                .withBody(body)
                .request(Exchange.class);

        assertNull(result.getException());
        UpsertRequest upsert = assertInstanceOf(UpsertRequest.class, singleRequest());
        assertEquals("vector-1", upsert.getVectors(0).getId());
        assertEquals(VECTOR, upsert.getVectors(0).getValuesList());
    }

    @ParameterizedTest
    @MethodSource("vectorBodies")
    void updateTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("pinecone:test?action=UPDATE")
                .withHeader(PineconeVectorDbHeaders.INDEX_ID, "vector-1")
                .withBody(body)
                .request(Exchange.class);

        assertNull(result.getException());
        UpdateRequest update = assertInstanceOf(UpdateRequest.class, singleRequest());
        assertEquals("vector-1", update.getId());
        assertEquals(VECTOR, update.getValuesList());
    }

    @ParameterizedTest
    @MethodSource("vectorBodies")
    void queryTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("pinecone:test?action=QUERY")
                .withHeader(PineconeVectorDbHeaders.QUERY_TOP_K, 3)
                .withBody(body)
                .request(Exchange.class);

        assertNull(result.getException());
        QueryRequest query = assertInstanceOf(QueryRequest.class, singleRequest());
        assertEquals(VECTOR, query.getVectorList());
        assertEquals(3, query.getTopK());
    }

    @Test
    void queryWithoutTheTopKHeaderReturnsTenMatches() {
        Exchange result = fluentTemplate.to("pinecone:test?action=QUERY")
                .withBody(VECTOR)
                .request(Exchange.class);

        assertNull(result.getException());
        assertInstanceOf(QueryResponseWithUnsignedIndices.class, result.getMessage().getBody());
        assertEquals(10, assertInstanceOf(QueryRequest.class, singleRequest()).getTopK());
    }

    @Test
    void queryByIdWithoutTheTopKHeaderReturnsTenMatches() {
        Exchange result = fluentTemplate.to("pinecone:test?action=QUERY_BY_ID")
                .withHeader(PineconeVectorDbHeaders.INDEX_ID, "vector-1")
                .request(Exchange.class);

        assertNull(result.getException());
        QueryRequest query = assertInstanceOf(QueryRequest.class, singleRequest());
        assertEquals("vector-1", query.getId());
        assertEquals(10, query.getTopK());
    }

    @Test
    void upsertWithoutTheIdHeaderGeneratesOne() {
        Exchange result = fluentTemplate.to("pinecone:test?action=UPSERT")
                .withBody(VECTOR)
                .request(Exchange.class);

        assertNull(result.getException());
        String id = result.getMessage().getHeader(PineconeVectorDbHeaders.INDEX_ID, String.class);
        assertNotNull(id);
        assertEquals(id, UUID.fromString(id).toString());
        UpsertRequest upsert = assertInstanceOf(UpsertRequest.class, singleRequest());
        assertEquals(id, upsert.getVectors(0).getId());
        assertEquals(VECTOR, upsert.getVectors(0).getValuesList());
    }

    @Test
    void theRagTransformerReadsTheMatchesOfAQuery() {
        Object texts = fluentTemplate.to("direct:rag")
                .withHeader(PineconeVectorDbHeaders.QUERY_TOP_K, 3)
                .withHeader(PineconeVectorDbHeaders.QUERY_INCLUDE_METADATA, true)
                .withBody(VECTOR)
                .request();

        assertEquals(List.of("hello"), texts);
    }

    @Test
    void aBodyThatIsNotAVectorFails() {
        Exchange text = fluentTemplate.to("pinecone:test?action=UPSERT")
                .withHeader(PineconeVectorDbHeaders.INDEX_ID, "vector-1")
                .withBody("0.1, 0.2, 0.3")
                .request(Exchange.class);
        InvalidPayloadException invalidPayload = assertInstanceOf(InvalidPayloadException.class, text.getException());
        assertEquals(List.class, invalidPayload.getType());
        assertTrue(invalidPayload.getMessage()
                .contains("the vector must be a List of numbers, such as Float or Double, a float[] or a double[]"),
                invalidPayload.getMessage());

        // the message names the type of the element, not its value
        Exchange listOfText = fluentTemplate.to("pinecone:test?action=QUERY")
                .withBody(List.of("secret-0.1", "0.2"))
                .request(Exchange.class);
        IllegalArgumentException notANumber = assertInstanceOf(IllegalArgumentException.class, listOfText.getException());
        assertTrue(notANumber.getMessage().startsWith("Vector element at index 0 is a java.lang.String"),
                notANumber.getMessage());
        assertFalse(notANumber.getMessage().contains("secret"), notANumber.getMessage());

        Exchange nullElement = fluentTemplate.to("pinecone:test?action=UPDATE")
                .withHeader(PineconeVectorDbHeaders.INDEX_ID, "vector-1")
                .withBody(Arrays.asList(0.1d, null, 0.3d))
                .request(Exchange.class);
        IllegalArgumentException isNull = assertInstanceOf(IllegalArgumentException.class, nullElement.getException());
        assertTrue(isNull.getMessage().startsWith("Vector element at index 1 is null"), isNull.getMessage());

        assertEquals(List.of(), requests);
    }

    private Object singleRequest() {
        assertEquals(1, requests.size(), requests::toString);
        return requests.get(0);
    }

    private void describeIndex(HttpExchange http) throws IOException {
        // the index, which the data plane of the fake serves
        String json = "{\"name\":\"" + indexName + "\",\"dimension\":3,\"metric\":\"cosine\","
                      + "\"host\":\"localhost:" + dataPlane.getPort() + "\","
                      + "\"spec\":{\"serverless\":{\"cloud\":\"aws\",\"region\":\"us-east-1\"}},"
                      + "\"status\":{\"ready\":true,\"state\":\"Ready\"}}";
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json");
        http.sendResponseHeaders(200, bytes.length);
        http.getResponseBody().write(bytes);
        http.close();
    }
}
