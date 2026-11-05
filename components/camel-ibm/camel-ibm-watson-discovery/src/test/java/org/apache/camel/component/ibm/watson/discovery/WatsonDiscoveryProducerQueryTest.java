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
package org.apache.camel.component.ibm.watson.discovery;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ibm.cloud.sdk.core.security.IamAuthenticator;
import com.ibm.watson.discovery.v2.model.QueryResponse;
import com.ibm.watson.discovery.v2.model.QueryResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The query operation parses the response of Watson Discovery, with a JDK HttpServer as the Discovery service and as
 * the IAM token service. The IBM SDK core must work with the Gson that Camel uses: sdk-core 9.20.0 of the Watson SDK
 * uses the Gson internal class $Gson$Types, which Gson 2.13.0 renamed to GsonTypes, so every query failed with a
 * NoClassDefFoundError while parsing the response.
 */
class WatsonDiscoveryProducerQueryTest extends CamelTestSupport {

    private static final String PROJECT_ID = "my-project";

    private HttpServer server;
    private volatile String queryResponse;
    private volatile String queryRequestUri;
    private volatile String queryRequestBody;
    private volatile String queryAuthorization;

    @Override
    protected void setupResources() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        // the IAM token service, which the IamAuthenticator of the endpoint asks for a token for the API key
        server.createContext("/identity/token", exchange -> {
            exchange.getRequestBody().readAllBytes();
            respond(exchange, "{\"access_token\":\"test-access-token\",\"refresh_token\":\"test-refresh-token\","
                              + "\"token_type\":\"Bearer\",\"expires_in\":3600,\"expiration\":"
                              + (Instant.now().getEpochSecond() + 3600) + "}");
        });
        // the query of the Discovery v2 API
        server.createContext("/v2/projects/" + PROJECT_ID + "/query", exchange -> {
            queryRequestUri = exchange.getRequestURI().toString();
            queryRequestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            queryAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            respond(exchange, queryResponse);
        });
        server.start();
    }

    @Override
    protected void cleanupResources() throws Exception {
        if (server != null) {
            server.stop(0);
        }
    }

    @BeforeEach
    void useStubTokenService() {
        // the endpoint authenticates with an IAM API key: take the token from the stub instead of IBM Cloud
        WatsonDiscoveryEndpoint endpoint = context.getEndpoint(discoveryUri(), WatsonDiscoveryEndpoint.class);
        ((IamAuthenticator) endpoint.getDiscoveryClient().getAuthenticator()).setURL(serverUrl());
    }

    @Test
    void queryParsesResponseWithoutResults() {
        queryResponse = "{\"matching_results\":0}";

        QueryResponse response = template.requestBody("direct:query", "What is Apache Camel?", QueryResponse.class);

        assertEquals(0L, response.getMatchingResults());
        assertNull(response.getResults());
    }

    @Test
    void queryParsesResultsWithDynamicProperties() {
        queryResponse = "{\"matching_results\":1,\"results\":[{\"document_id\":\"doc-1\",\"title\":\"Apache Camel\","
                        + "\"result_metadata\":{\"collection_id\":\"col-1\",\"confidence\":0.9}}]}";

        QueryResponse response = template.requestBody("direct:query", "What is Apache Camel?", QueryResponse.class);

        assertEquals(1L, response.getMatchingResults());
        assertEquals(1, response.getResults().size());
        QueryResult result = response.getResults().get(0);
        assertEquals("doc-1", result.getDocumentId());
        // a property the model does not declare is kept as a dynamic property of the result
        assertEquals("Apache Camel", result.get("title"));
        assertEquals("col-1", result.getResultMetadata().getCollectionId());

        assertEquals("/v2/projects/" + PROJECT_ID + "/query?version=2023-03-31", queryRequestUri);
        JsonObject request = JsonParser.parseString(queryRequestBody).getAsJsonObject();
        assertEquals("What is Apache Camel?", request.get("natural_language_query").getAsString());
        assertEquals("Bearer test-access-token", queryAuthorization);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:query").to(discoveryUri());
            }
        };
    }

    private String serverUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    private String discoveryUri() {
        return "ibm-watson-discovery:test?apiKey=test-api-key&projectId=" + PROJECT_ID + "&operation=query&serviceUrl="
               + serverUrl();
    }

    private static void respond(HttpExchange exchange, String json) throws IOException {
        byte[] body = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
