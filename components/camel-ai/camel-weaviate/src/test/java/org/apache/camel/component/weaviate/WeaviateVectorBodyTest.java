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
package org.apache.camel.component.weaviate;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.weaviate.client6.v1.internal.grpc.ByteStringUtil;
import io.weaviate.client6.v1.internal.grpc.protocol.WeaviateGrpc;
import io.weaviate.client6.v1.internal.grpc.protocol.WeaviateProtoBase.Vectors;
import io.weaviate.client6.v1.internal.grpc.protocol.WeaviateProtoSearchGet.SearchReply;
import io.weaviate.client6.v1.internal.grpc.protocol.WeaviateProtoSearchGet.SearchRequest;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CAMEL-26214: the vector of CREATE, UPDATE_BY_ID and QUERY, and the CamelWeaviateQueryVector header of HYBRID_QUERY,
 * is a List of numbers, such as the List of Double a JSON array unmarshals to, or a float or double array. A fake
 * Weaviate, its REST API on a JDK HttpServer and its gRPC API, records the requests of the client, so no Weaviate is
 * needed.
 */
class WeaviateVectorBodyTest extends CamelTestSupport {

    private static final float[] VECTOR = { 0.1f, 0.2f, 0.3f };
    // the vector as the client writes it in the JSON of an object
    private static final String VECTOR_JSON = "\"vectors\":{\"default\":[0.1,0.2,0.3]}";
    private static final String ID = "6f0b5a6e-4b5e-4b8e-9d5e-0a8c7a2a9a11";

    private final List<String> restRequests = new CopyOnWriteArrayList<>();
    private final List<SearchRequest> searches = new CopyOnWriteArrayList<>();
    private HttpServer rest;
    private Server grpc;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        rest = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        rest.createContext("/", this::handle);
        rest.start();
        grpc = ServerBuilder.forPort(0).addService(new WeaviateGrpc.WeaviateImplBase() {
            @Override
            public void search(SearchRequest request, StreamObserver<SearchReply> reply) {
                searches.add(request);
                reply.onNext(SearchReply.getDefaultInstance());
                reply.onCompleted();
            }
        }).build().start();

        CamelContext context = super.createCamelContext();
        WeaviateVectorDbConfiguration configuration
                = context.getComponent("weaviate", WeaviateVectorDbComponent.class).getConfiguration();
        configuration.setHost("localhost:" + rest.getAddress().getPort());
        configuration.setGrpcHost("localhost");
        configuration.setGrpcPort(grpc.getPort());
        return context;
    }

    @AfterEach
    void stopFakeWeaviate() {
        rest.stop(0);
        grpc.shutdownNow();
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
    void createTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("weaviate:Articles?action=CREATE")
                .withBody(body)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(restRequests).filteredOn(request -> request.startsWith("POST /v1/objects"))
                .singleElement().asString().contains(VECTOR_JSON);
    }

    @ParameterizedTest
    @MethodSource("vectorBodies")
    void updateByIdTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("weaviate:Articles?action=UPDATE_BY_ID")
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, ID)
                .withBody(body)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(restRequests).filteredOn(request -> request.startsWith("PATCH /v1/objects/Articles/" + ID))
                .singleElement().asString().contains(VECTOR_JSON);
    }

    @ParameterizedTest
    @MethodSource("vectorBodies")
    void queryTakesTheVector(Object body) {
        Exchange result = fluentTemplate.to("weaviate:Articles?action=QUERY")
                .withBody(body)
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(searches).singleElement()
                .satisfies(search -> assertThat(vectorOf(search.getNearVector().getVectorsList())).containsExactly(VECTOR));
    }

    @Test
    void hybridQueryTakesTheQueryVectorHeader() {
        Exchange result = fluentTemplate.to("weaviate:Articles?action=HYBRID_QUERY")
                .withHeader(WeaviateVectorDbHeaders.QUERY_VECTOR, List.of(0.1d, 0.2d, 0.3d))
                .withBody("camel")
                .request(Exchange.class);

        assertThat(result.getException()).isNull();
        assertThat(searches).singleElement().satisfies(search -> {
            assertThat(search.getHybridSearch().getQuery()).isEqualTo("camel");
            assertThat(vectorOf(search.getHybridSearch().getNearVector().getVectorsList())).containsExactly(VECTOR);
        });
    }

    @Test
    void aBodyThatIsNotAVectorFails() {
        Exchange text = fluentTemplate.to("weaviate:Articles?action=CREATE")
                .withBody("0.1, 0.2, 0.3")
                .request(Exchange.class);
        assertThat(text.getException()).isInstanceOfSatisfying(InvalidPayloadException.class,
                e -> assertThat(e.getType()).isEqualTo(List.class))
                .hasMessageContaining("the vector must be a List of numbers, such as Float or Double, a float[] or a double[]");

        // the message names the type of the element, not its value
        Exchange listOfText = fluentTemplate.to("weaviate:Articles?action=QUERY")
                .withBody(List.of("secret-0.1", "0.2"))
                .request(Exchange.class);
        assertThat(listOfText.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Vector element at index 0 is a java.lang.String")
                .hasMessageNotContaining("secret");

        Exchange nullElement = fluentTemplate.to("weaviate:Articles?action=UPDATE_BY_ID")
                .withHeader(WeaviateVectorDbHeaders.INDEX_ID, ID)
                .withBody(Arrays.asList(0.1d, null, 0.3d))
                .request(Exchange.class);
        assertThat(nullElement.getException()).isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("Vector element at index 1 is null");

        assertThat(restRequests).noneMatch(request -> request.contains("/objects"));
        assertThat(searches).isEmpty();
    }

    private static float[] vectorOf(List<Vectors> vectors) {
        assertThat(vectors).hasSize(1);
        return ByteStringUtil.decodeVectorSingle(vectors.get(0).getVectorBytes());
    }

    private void handle(HttpExchange http) throws IOException {
        String body = new String(http.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = http.getRequestURI().getPath();
        restRequests.add(http.getRequestMethod() + " " + path + " " + body);
        String response;
        if (path.endsWith("/.well-known/live")) {
            response = "";
        } else if (path.endsWith("/meta")) {
            response = "{\"hostname\":\"http://[::]:8080\",\"version\":\"1.37.4\",\"modules\":{}}";
        } else {
            // the object, as Weaviate gives it back
            response = body;
        }
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        http.getResponseHeaders().add("Content-Type", "application/json");
        http.sendResponseHeaders(200, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            http.getResponseBody().write(bytes);
        }
        http.close();
    }
}
