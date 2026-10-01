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
package org.apache.camel.component.odata;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.google.common.io.Resources;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.http.base.HttpOperationFailedException;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ODataProducerTest extends CamelTestSupport {

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort()
                    .dynamicHttpsPort()
                    .keystorePath(Resources.getResource("localhost.p12").toString())
                    .keystoreType("PKCS12")
                    .keystorePassword("changeit")
                    .keyManagerPassword("changeit"))
            .build();

    private ODataEndpoint endpoint;
    private Producer producer;

    @BeforeEach
    public void setUpProducer() throws Exception {
        String uri = "odata:http://localhost:" + wireMock.getPort() + "/odata/Products";
        endpoint = context.getEndpoint(uri, ODataEndpoint.class);
        producer = endpoint.createProducer();
        producer.start();
    }

    @AfterEach
    public void tearDownProducer() throws Exception {
        if (producer != null) {
            producer.stop();
        }
    }

    @Test
    void testProcessGetCollection() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[{\"ID\":101,\"Name\":\"Laptop\"},{\"ID\":102,\"Name\":\"Phone\"}]}")));

        Exchange exchange = endpoint.createExchange();
        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        Map<?, ?> body = exchange.getMessage().getBody(Map.class);
        assertNotNull(body);
        assertTrue(body.containsKey("value"));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products")));
    }

    @Test
    void testProcessGetSingleEntityWithKeyHeaderDynamicUri() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products(101)"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ID\":101,\"Name\":\"Laptop\"}")));

        Exchange exchange = endpoint.createExchange();
        exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);
        exchange.getMessage().setHeader(ODataConstants.KEY, "101");

        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        Map<?, ?> body = exchange.getMessage().getBody(Map.class);
        assertNotNull(body);
        assertEquals("Laptop", body.get("Name"));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products(101)")));
    }

    @Test
    void testProcessGetSingleEntityWithKeyInUri() throws Exception {
        ODataEndpoint singleEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(101)", ODataEndpoint.class);
        Producer singleProducer = singleEndpoint.createProducer();
        singleProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products(101)"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"ID\":101,\"Name\":\"Laptop\"}")));

            Exchange exchange = singleEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);

            singleProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            Map<?, ?> body = exchange.getMessage().getBody(Map.class);
            assertNotNull(body);
            assertEquals("Laptop", body.get("Name"));

            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products(101)")));
        } finally {
            singleProducer.stop();
        }
    }

    @Test
    void testProcessQueryOptionsHeaders() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .withQueryParam("$filter", matching(".*Name.*Laptop.*"))
                .withQueryParam("$select", matching(".*ID.*Name.*"))
                .withQueryParam("$expand", equalTo("Category"))
                .withQueryParam("$orderby", matching(".*Name.*asc.*"))
                .withQueryParam("$top", equalTo("5"))
                .withQueryParam("$skip", equalTo("10"))
                .withQueryParam("$count", equalTo("true"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"@odata.count\":1,\"value\":[{\"ID\":101,\"Name\":\"Laptop\"}]}")));

        Exchange exchange = endpoint.createExchange();
        exchange.getMessage().setHeader(ODataConstants.FILTER, "Name eq 'Laptop'");
        exchange.getMessage().setHeader(ODataConstants.SELECT, "ID,Name");
        exchange.getMessage().setHeader(ODataConstants.EXPAND, "Category");
        exchange.getMessage().setHeader(ODataConstants.ORDER_BY, "Name asc");
        exchange.getMessage().setHeader(ODataConstants.TOP, 5);
        exchange.getMessage().setHeader(ODataConstants.SKIP, 10);
        exchange.getMessage().setHeader(ODataConstants.INCLUDE_COUNT, true);

        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals(1L, exchange.getMessage().getHeader(ODataConstants.COUNT));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                .withQueryParam("$filter", matching(".*Name.*Laptop.*"))
                .withQueryParam("$select", matching(".*ID.*Name.*"))
                .withQueryParam("$expand", equalTo("Category"))
                .withQueryParam("$orderby", matching(".*Name.*asc.*"))
                .withQueryParam("$top", equalTo("5"))
                .withQueryParam("$skip", equalTo("10"))
                .withQueryParam("$count", equalTo("true")));
    }

    @Test
    void testProcessCreateEntity() throws Exception {
        wireMock.stubFor(post(urlPathEqualTo("/odata/Products"))
                .withRequestBody(equalToJson("{\"Name\":\"Tablet\",\"Price\":299.99}"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"ID\":103,\"Name\":\"Tablet\",\"Price\":299.99}")));

        Exchange exchange = endpoint.createExchange();
        exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.CREATE);

        Map<String, Object> payload = new HashMap<>();
        payload.put("Name", "Tablet");
        payload.put("Price", 299.99);
        exchange.getMessage().setBody(payload);

        producer.process(exchange);

        assertEquals(201, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        Map<?, ?> body = exchange.getMessage().getBody(Map.class);
        assertNotNull(body);
        assertEquals(new BigDecimal(103), body.get("ID"));
    }

    @Test
    void testProcessIgnoresInboundHttpRoutingHeaders() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[]}")));

        Exchange exchange = endpoint.createExchange();

        // Simulate HTTP/REST headers inherited from an upstream consumer.
        exchange.getMessage().setHeader(Exchange.HTTP_URI, "http://malicious.example/other");
        exchange.getMessage().setHeader(Exchange.HTTP_PATH, "/wrong/path");
        exchange.getMessage().setHeader(Exchange.HTTP_QUERY, "unexpected=true");
        exchange.getMessage().setHeader(Exchange.HTTP_RAW_QUERY, "rawUnexpected=true");
        exchange.getMessage().setHeader("CamelRestHttpUri", "http://malicious.example/rest");

        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products")));
    }

    @Test
    void testProcessUpdateEntity() throws Exception {
        ODataEndpoint keyEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(101)", ODataEndpoint.class);
        Producer keyProducer = keyEndpoint.createProducer();
        keyProducer.start();

        try {
            wireMock.stubFor(patch(urlPathEqualTo("/odata/Products(101)"))
                    .withRequestBody(equalToJson("{\"Name\":\"Updated Laptop\"}"))
                    .willReturn(aResponse().withStatus(204)));

            Exchange exchange = keyEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.UPDATE);

            Map<String, Object> payload = new HashMap<>();
            payload.put("Name", "Updated Laptop");
            exchange.getMessage().setBody(payload);

            keyProducer.process(exchange);

            assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        } finally {
            keyProducer.stop();
        }
    }

    @Test
    void testProcessDeleteEntity() throws Exception {
        ODataEndpoint keyEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(101)", ODataEndpoint.class);
        Producer keyProducer = keyEndpoint.createProducer();
        keyProducer.start();

        try {
            wireMock.stubFor(delete(urlPathEqualTo("/odata/Products(101)"))
                    .willReturn(aResponse().withStatus(204)));

            Exchange exchange = keyEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.DELETE);

            keyProducer.process(exchange);

            assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertNull(exchange.getMessage().getBody());
        } finally {
            keyProducer.stop();
        }
    }

    @Test
    void testProcessGetSingleEntityWithETagHeader() throws Exception {
        ODataEndpoint singleEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(101)", ODataEndpoint.class);
        Producer singleProducer = singleEndpoint.createProducer();
        singleProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products(101)"))
                    .withHeader("If-Match", equalTo("\"etag-v1\""))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"ID\":101,\"Name\":\"Laptop\",\"@odata.etag\":\"etag-v2\"}")));

            Exchange exchange = singleEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);
            exchange.getMessage().setHeader(ODataConstants.ETAG, "\"etag-v1\"");

            singleProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertEquals("etag-v2", exchange.getMessage().getHeader(ODataConstants.ETAG));
        } finally {
            singleProducer.stop();
        }
    }

    @Test
    void testProcessGetSingleEntityWithHttpETag() throws Exception {
        ODataEndpoint singleEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(102)", ODataEndpoint.class);
        Producer singleProducer = singleEndpoint.createProducer();
        singleProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products(102)"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withHeader("ETag", "W/\"etag-v3\"")
                            .withBody("{\"ID\":102,\"Name\":\"Monitor\"}")));

            Exchange exchange = singleEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);

            singleProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertEquals("W/\"etag-v3--gzip\"", exchange.getMessage().getHeader(ODataConstants.ETAG));
        } finally {
            singleProducer.stop();
        }
    }

    @Test
    void testProcessUpdateEntityWithETag() throws Exception {
        ODataEndpoint updateEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(101)", ODataEndpoint.class);
        Producer updateProducer = updateEndpoint.createProducer();
        updateProducer.start();

        try {
            wireMock.stubFor(patch(urlPathEqualTo("/odata/Products(101)"))
                    .withHeader("If-Match", equalTo("W/\"etag-v3\""))
                    .withHeader("Content-Type", containing("application/json"))
                    .withRequestBody(equalToJson("{\"Name\":\"Updated Laptop\"}"))
                    .willReturn(aResponse()
                            .withStatus(204)
                            .withHeader("ETag", "W/\"etag-v4\"")));

            Exchange exchange = updateEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.UPDATE);
            exchange.getMessage().setHeader(ODataConstants.ETAG, "W/\"etag-v3\"");
            exchange.getMessage().setBody("{\"Name\":\"Updated Laptop\"}");

            updateProducer.process(exchange);

            assertEquals(204, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertEquals("W/\"etag-v4\"", exchange.getMessage().getHeader(ODataConstants.ETAG));

            wireMock.verify(patchRequestedFor(urlPathEqualTo("/odata/Products(101)"))
                    .withHeader("If-Match", equalTo("W/\"etag-v3\"")));
        } finally {
            updateProducer.stop();
        }
    }

    @Test
    void testProcessUpdateEntityWithETagPreconditionFailed() throws Exception {
        ODataEndpoint updateEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(101)", ODataEndpoint.class);
        Producer updateProducer = updateEndpoint.createProducer();
        updateProducer.start();

        try {
            wireMock.stubFor(patch(urlPathEqualTo("/odata/Products(101)"))
                    .withHeader("If-Match", equalTo("W/\"stale-etag\""))
                    .willReturn(aResponse()
                            .withStatus(412)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "error": {
                                        "code": "412",
                                        "message": "The ETag does not match."
                                      }
                                    }
                                    """)));

            Exchange exchange = updateEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.UPDATE);
            exchange.getMessage().setHeader(ODataConstants.ETAG, "W/\"stale-etag\"");
            exchange.getMessage().setBody("{\"Name\":\"Updated Laptop\"}");

            HttpOperationFailedException exception = assertThrows(
                    HttpOperationFailedException.class,
                    () -> updateProducer.process(exchange));

            assertEquals(412, exception.getStatusCode());
            wireMock.verify(patchRequestedFor(urlPathEqualTo("/odata/Products(101)"))
                    .withHeader("If-Match", equalTo("W/\"stale-etag\"")));
        } finally {
            updateProducer.stop();
        }
    }

    @Test
    void testProcessReadSetWithEncodedFilter() throws Exception {
        ODataEndpoint readEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products",
                ODataEndpoint.class);
        Producer readProducer = readEndpoint.createProducer();
        readProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .withQueryParam("$filter", equalTo("Name eq 'Laptop Pro'"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "value": [
                                        {"ID": 101, "Name": "Laptop Pro"}
                                      ]
                                    }
                                    """)));

            Exchange exchange = readEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_SET);
            exchange.getMessage().setHeader(ODataConstants.FILTER, "Name eq 'Laptop Pro'");

            readProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withQueryParam("$filter", equalTo("Name eq 'Laptop Pro'")));
        } finally {
            readProducer.stop();
        }
    }

    @Test
    void testProcessReadSetWithComplexEncodedFilter() throws Exception {
        ODataEndpoint readEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products",
                ODataEndpoint.class);
        Producer readProducer = readEndpoint.createProducer();
        readProducer.start();

        String filter = "Name eq 'O''Reilly' and Category eq 'Office/Supplies' "
                        + "and Price gt 10.5 and (Status eq 'Active' or Code eq 'A%20B') "
                        + "and startswith(Name,'Pro')";

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .withQueryParam("$filter", equalTo(filter))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "value": [
                                        {
                                          "ID": 101,
                                          "Name": "O'Reilly Pro",
                                          "Category": "Office/Supplies"
                                        }
                                      ]
                                    }
                                    """)));

            Exchange exchange = readEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_SET);
            exchange.getMessage().setHeader(ODataConstants.FILTER, filter);

            readProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withQueryParam("$filter", equalTo(filter)));
        } finally {
            readProducer.stop();
        }
    }

    @Test
    void testProcessReadEntryWithTrailingSlashAndKey() throws Exception {
        ODataEndpoint singleEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products/",
                ODataEndpoint.class);
        Producer singleProducer = singleEndpoint.createProducer();
        singleProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products(101)"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "ID": 101,
                                      "Name": "Laptop"
                                    }
                                    """)));

            Exchange exchange = singleEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);
            exchange.getMessage().setHeader(ODataConstants.KEY, "101");

            singleProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products(101)")));
        } finally {
            singleProducer.stop();
        }
    }

    @Test
    void testProcessNextLinkHeader() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[],\"@odata.nextLink\":\"http://localhost/odata/Products?$skip=10\"}")));

        Exchange exchange = endpoint.createExchange();
        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("http://localhost/odata/Products?$skip=10", exchange.getMessage().getHeader(ODataConstants.NEXT_LINK));
    }

    @Test
    void testProcessEndpointConfigurationDefaults() throws Exception {
        ODataEndpoint configuredEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products?top=5&skip=10&filter=Name eq 'Phone'",
                ODataEndpoint.class);
        Producer configuredProducer = configuredEndpoint.createProducer();
        configuredProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .withQueryParam("$top", equalTo("5"))
                    .withQueryParam("$skip", equalTo("10"))
                    .withQueryParam("$filter", matching(".*Name.*Phone.*"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"value\":[{\"ID\":102,\"Name\":\"Phone\"}]}")));

            Exchange exchange = configuredEndpoint.createExchange();
            configuredProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        } finally {
            configuredProducer.stop();
        }
    }

    @Test
    void testProcessHeaderOverridesEndpointConfiguration() throws Exception {
        ODataEndpoint configuredEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products?top=5",
                ODataEndpoint.class);
        Producer configuredProducer = configuredEndpoint.createProducer();
        configuredProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .withQueryParam("$top", equalTo("20"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"value\":[]}")));

            Exchange exchange = configuredEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.TOP, 20);

            configuredProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        } finally {
            configuredProducer.stop();
        }
    }

    @Test
    void testProcessHttpErrorPropagatesException() {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse().withStatus(500)));

        Exchange exchange = endpoint.createExchange();

        assertThrows(HttpOperationFailedException.class, () -> producer.process(exchange));
    }

    @Test
    void testProcessEmptyResponseBodyReturnsNullBody() throws Exception {
        ODataEndpoint singleEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products(101)", ODataEndpoint.class);
        Producer singleProducer = singleEndpoint.createProducer();
        singleProducer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products(101)"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("")));

            Exchange exchange = singleEndpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);

            singleProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertNull(exchange.getMessage().getBody());
        } finally {
            singleProducer.stop();
        }
    }

    @Test
    void testProducerSingletonAndLifecycle() {
        assertTrue(producer.isSingleton());
    }

    @Test
    void testBasicAuthentication() throws Exception {
        String username = "adminUser";
        String password = "adminPassword123";
        String expectedHeader
                = "Basic " + Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

        ODataEndpoint authEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort()
                                                         + "/odata/Products?authMethod=Basic&authUsername=" + username
                                                         + "&authPassword=" + password,
                ODataEndpoint.class);
        Producer authProducer = authEndpoint.createProducer();
        authProducer.start();

        try {
            // Priority 1: Match and return 200 OK when correct credentials are present
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .atPriority(1)
                    .withHeader("Authorization", equalTo(expectedHeader))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"value\":[]}")));

            // Priority 10: Fallback HTTP Basic 401 challenge with WWW-Authenticate header
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .atPriority(10)
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("WWW-Authenticate", "Basic realm=\"OData Test Realm\"")));

            Exchange exchange = authEndpoint.createExchange();
            authProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo(expectedHeader)));
        } finally {
            authProducer.stop();
        }
    }

    @Test
    void testBearerAuthenticationAndExchangeHeaderPrecedence() throws Exception {
        String configuredToken = "configuredBearerToken123";
        String explicitHeaderToken = "Bearer explicitHeaderToken456";

        ODataEndpoint authEndpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort()
                                                         + "/odata/Products?authBearerToken=" + configuredToken,
                ODataEndpoint.class);
        Producer authProducer = authEndpoint.createProducer();
        authProducer.start();

        try {
            // Case 1: Configured bearer token attached automatically
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo("Bearer " + configuredToken))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"value\":[]}")));

            Exchange exchange1 = authEndpoint.createExchange();
            authProducer.process(exchange1);
            assertEquals(200, exchange1.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo("Bearer " + configuredToken)));

            // Case 2: Explicit Exchange Authorization header takes precedence over configured token
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo(explicitHeaderToken))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"value\":[]}")));

            Exchange exchange2 = authEndpoint.createExchange();
            exchange2.getMessage().setHeader("Authorization", explicitHeaderToken);
            authProducer.process(exchange2);
            assertEquals(200, exchange2.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo(explicitHeaderToken)));
        } finally {
            authProducer.stop();
        }
    }

    @Test
    void testBearerEndpointConcurrentIsolation() throws Exception {
        String token1 = "tokenUnit1";
        String token2 = "tokenUnit2";

        ODataEndpoint endpoint1 = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products?authBearerToken=" + token1,
                ODataEndpoint.class);
        ODataEndpoint endpoint2 = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products?authBearerToken=" + token2,
                ODataEndpoint.class);

        Producer producer1 = endpoint1.createProducer();
        Producer producer2 = endpoint2.createProducer();

        producer1.start();
        producer2.start();

        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[]}")));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

        try {
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    Exchange exchange = null;
                    try {
                        Producer targetProducer = (index % 2 == 0) ? producer1 : producer2;
                        ODataEndpoint targetEndpoint = (index % 2 == 0) ? endpoint1 : endpoint2;
                        exchange = targetEndpoint.createExchange();
                        targetProducer.process(exchange);
                        if (exchange.getException() != null) {
                            failures.add(exchange.getException());
                        }
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertTrue(failures.isEmpty(), "Worker threads encountered exceptions: " + failures);

            wireMock.verify(threadCount / 2, getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo("Bearer " + token1)));
            wireMock.verify(threadCount / 2, getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo("Bearer " + token2)));
        } finally {
            executor.shutdownNow();
            producer1.stop();
            producer2.stop();
        }
    }

    @Test
    void testBasicAuthConcurrentIsolation() throws Exception {
        String user1 = "user1";
        String pass1 = "pass1";
        String expectedHeader1
                = "Basic " + Base64.getEncoder().encodeToString((user1 + ":" + pass1).getBytes(StandardCharsets.UTF_8));

        String user2 = "user2";
        String pass2 = "pass2";
        String expectedHeader2
                = "Basic " + Base64.getEncoder().encodeToString((user2 + ":" + pass2).getBytes(StandardCharsets.UTF_8));

        ODataEndpoint endpoint1 = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort()
                                                      + "/odata/Products?authMethod=Basic&authUsername=" + user1
                                                      + "&authPassword=" + pass1,
                ODataEndpoint.class);
        ODataEndpoint endpoint2 = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort()
                                                      + "/odata/Products?authMethod=Basic&authUsername=" + user2
                                                      + "&authPassword=" + pass2,
                ODataEndpoint.class);

        Producer producer1 = endpoint1.createProducer();
        Producer producer2 = endpoint2.createProducer();

        producer1.start();
        producer2.start();

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        try {
            // Priority 1: Match and return 200 OK when user1 credentials are present
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .atPriority(1)
                    .withHeader("Authorization", equalTo(expectedHeader1))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"value\":[]}")));

            // Priority 1: Match and return 200 OK when user2 credentials are present
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .atPriority(1)
                    .withHeader("Authorization", equalTo(expectedHeader2))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"value\":[]}")));

            // Priority 10: Fallback HTTP Basic 401 challenge with WWW-Authenticate header
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .atPriority(10)
                    .willReturn(aResponse()
                            .withStatus(401)
                            .withHeader("WWW-Authenticate", "Basic realm=\"OData Test Realm\"")));

            CountDownLatch latch = new CountDownLatch(threadCount);
            ConcurrentLinkedQueue<Throwable> failures = new ConcurrentLinkedQueue<>();

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    Exchange exchange = null;
                    try {
                        Producer targetProducer = (index % 2 == 0) ? producer1 : producer2;
                        ODataEndpoint targetEndpoint = (index % 2 == 0) ? endpoint1 : endpoint2;
                        exchange = targetEndpoint.createExchange();
                        targetProducer.process(exchange);
                        if (exchange.getException() != null) {
                            failures.add(exchange.getException());
                        }
                    } catch (Throwable t) {
                        failures.add(t);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertTrue(failures.isEmpty(), "Worker threads encountered exceptions: " + failures);

            wireMock.verify(threadCount / 2, getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo(expectedHeader1)));
            wireMock.verify(threadCount / 2, getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo(expectedHeader2)));
        } finally {
            executor.shutdownNow();
            producer1.stop();
            producer2.stop();
        }
    }

    @Test
    void testQueryEncodingWithAmpersand() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .withQueryParam("$filter", matching(".*Name.*John.*Sons.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[]}")));

        Exchange exchange = endpoint.createExchange();
        exchange.getMessage().setHeader(ODataConstants.FILTER, "Name eq 'John & Sons'");

        producer.process(exchange);

        assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                .withQueryParam("$filter", matching(".*Name.*John.*Sons.*")));
    }

    @Test
    void testProcessReadEntryWithStringKey() throws Exception {
        ODataEndpoint endpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products",
                ODataEndpoint.class);
        Producer producer = endpoint.createProducer();
        producer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo("/odata/Products('ABC-123')"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"ID":"ABC-123","Name":"Laptop"}
                                    """)));

            Exchange exchange = endpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);
            exchange.getMessage().setHeader(ODataConstants.KEY, "'ABC-123'");

            producer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products('ABC-123')")));
        } finally {
            producer.stop();
        }
    }

    @Test
    void testProcessReadEntryWithCompositeKey() throws Exception {
        ODataEndpoint endpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products",
                ODataEndpoint.class);
        Producer producer = endpoint.createProducer();
        producer.start();

        try {
            wireMock.stubFor(get(urlPathEqualTo(
                    "/odata/Products(ProductID=101,CategoryID=5)"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {"ProductID":101,"CategoryID":5,"Name":"Laptop"}
                                    """)));

            Exchange exchange = endpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_ENTRY);
            exchange.getMessage().setHeader(
                    ODataConstants.KEY, "ProductID=101,CategoryID=5");

            producer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

            wireMock.verify(getRequestedFor(urlPathEqualTo(
                    "/odata/Products(ProductID=101,CategoryID=5)")));
        } finally {
            producer.stop();
        }
    }

    @Test
    void testProcessWithConfiguredBasicAuthentication() throws Exception {
        ODataEndpoint endpoint = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort() + "/odata/Products",
                ODataEndpoint.class);

        endpoint.getConfiguration().setAuthMethod("Basic");
        endpoint.getConfiguration().setAuthUsername("odata-user");
        endpoint.getConfiguration().setAuthPassword("odata-password");

        Producer producer = endpoint.createProducer();
        producer.start();

        try {
            String credentials = Base64.getEncoder()
                    .encodeToString("odata-user:odata-password".getBytes(StandardCharsets.UTF_8));

            wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo("Basic " + credentials))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("""
                                    {
                                      "value": []
                                    }
                                    """)));

            Exchange exchange = endpoint.createExchange();
            exchange.getMessage().setHeader(ODataConstants.OPERATION, ODataOperation.READ_SET);

            producer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));

            wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                    .withHeader("Authorization", equalTo("Basic " + credentials)));
        } finally {
            producer.stop();
        }
    }

    @Test
    void testProcessHttpsWithSslContextParameters() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[]}")));

        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setResource("localhost.p12");
        trustStore.setPassword("changeit");
        trustStore.setType("PKCS12");

        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStore);

        SSLContextParameters sslContextParameters = new SSLContextParameters();
        sslContextParameters.setTrustManagers(trustManagers);

        String uri = "odata:https://localhost:" + wireMock.getHttpsPort() + "/odata/Products";

        ODataEndpoint httpsEndpoint = context.getEndpoint(uri, ODataEndpoint.class);
        httpsEndpoint.setSslContextParameters(sslContextParameters);

        Producer httpsProducer = httpsEndpoint.createProducer();
        try {
            httpsProducer.start();

            Exchange exchange = httpsEndpoint.createExchange();
            httpsProducer.process(exchange);

            assertEquals(200, exchange.getMessage().getHeader(Exchange.HTTP_RESPONSE_CODE));
            assertNotNull(exchange.getMessage().getBody());
        } finally {
            httpsProducer.stop();
        }

        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products")));
    }

    @Test
    void testAuthenticationIsIsolatedBetweenEndpoints() throws Exception {
        wireMock.stubFor(get(urlPathEqualTo("/odata/Products"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"value\":[]}")));

        ODataEndpoint endpoint1 = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort()
                                                      + "/odata/Products?authMethod=Basic&authUsername=user1&authPassword=password1",
                ODataEndpoint.class);

        ODataEndpoint endpoint2 = context.getEndpoint(
                "odata:http://localhost:" + wireMock.getPort()
                                                      + "/odata/Products?authMethod=Basic&authUsername=user2&authPassword=password2",
                ODataEndpoint.class);

        Producer producer1 = endpoint1.createProducer();
        Producer producer2 = endpoint2.createProducer();

        try {
            producer1.start();
            producer2.start();

            Exchange exchange1 = endpoint1.createExchange();
            producer1.process(exchange1);

            Exchange exchange2 = endpoint2.createExchange();
            producer2.process(exchange2);
        } finally {
            producer1.stop();
            producer2.stop();
        }

        String expected1 = "Basic " + Base64.getEncoder()
                .encodeToString("user1:password1".getBytes(StandardCharsets.UTF_8));
        String expected2 = "Basic " + Base64.getEncoder()
                .encodeToString("user2:password2".getBytes(StandardCharsets.UTF_8));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                .withHeader("Authorization", equalTo(expected1)));

        wireMock.verify(getRequestedFor(urlPathEqualTo("/odata/Products"))
                .withHeader("Authorization", equalTo(expected2)));
    }
}
