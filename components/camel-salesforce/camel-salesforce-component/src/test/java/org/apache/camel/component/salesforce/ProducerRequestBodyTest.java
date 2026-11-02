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
package org.apache.camel.component.salesforce;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import mockwebserver3.Dispatcher;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.HttpUrl;
import org.apache.camel.Exchange;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.SalesforceException;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectBatch;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectComposite;
import org.apache.camel.component.salesforce.api.dto.composite.SObjectTree;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the request bodies the producer sends, and the errors it reports, against a mock Salesforce server.
 */
public class ProducerRequestBodyTest extends AbstractSalesforceTestBase {

    private static final String OAUTH2_TOKEN_PATH = "/services/oauth2/token";

    private static final LinkedBlockingQueue<RecordedRequest> REQUESTS = new LinkedBlockingQueue<>();

    private static MockWebServer server;
    private static HttpUrl loginUrl;

    @BeforeAll
    public static void startServer() throws IOException {
        server = new MockWebServer();
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) {
                final String path = recordedRequest.getUrl().encodedPath();
                if (path.equals(OAUTH2_TOKEN_PATH)) {
                    return new MockResponse.Builder()
                            .code(200)
                            .body("{ \"access_token\": \"mock_token\", \"id\": \"https://login.salesforce.com/id/00D4100000xxxxxxxx/0054100000xxxxxxxx\", \"instance_url\": \""
                                  + loginUrl + "\"}")
                            .build();
                }
                if (path.startsWith("/services/data/")) {
                    REQUESTS.add(recordedRequest);
                }
                if (path.contains("/composite")) {
                    // not the JSON of a composite response
                    return new MockResponse.Builder().code(200).body("not json").build();
                }
                return new MockResponse.Builder()
                        .code(201)
                        .addHeader("Content-Type", "application/json")
                        .body("{ \"id\": \"001D000000IqhSLIAZ\", \"errors\": [], \"success\": true }")
                        .build();
            }
        });
        server.start();
        loginUrl = server.url("");
    }

    @AfterAll
    public static void shutDownServer() throws IOException {
        if (server != null) {
            server.close();
        }
    }

    @BeforeEach
    public void clearRequests() {
        REQUESTS.clear();
    }

    @Override
    protected void createComponent() throws Exception {
        final SalesforceComponent component = new SalesforceComponent();
        final SalesforceEndpointConfig config = new SalesforceEndpointConfig();
        config.setApiVersion(SalesforceEndpointConfig.DEFAULT_VERSION);
        component.setConfig(config);

        final SalesforceLoginConfig loginConfig = new SalesforceLoginConfig();
        loginConfig.setClientId("ignored");
        loginConfig.setClientSecret("ignored");
        loginConfig.setRefreshToken("ignored");
        loginConfig.setLoginUrl(loginUrl.toString());
        component.setLoginConfig(loginConfig);
        component.setPackages(Merchandise__c.class.getPackage().getName());

        context().addComponent("salesforce", component);
    }

    @Test
    public void createSObjectShouldSendMapBodyAsJson() throws Exception {
        final CreateSObjectResult result = template().requestBody("direct:createSObject", Map.of("Name", "x"),
                CreateSObjectResult.class);

        assertThat(result.getId()).isEqualTo("001D000000IqhSLIAZ");
        final RecordedRequest request = REQUESTS.poll(10, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getBody().utf8()).isEqualTo("{\"Name\":\"x\"}");
    }

    @Test
    public void rawWithMapBodyShouldFail() {
        final Exchange exchange = template().request("direct:raw", e -> e.getIn().setBody(Map.of("Name", "x")));

        assertThat(exchange.getException()).isInstanceOf(InvalidPayloadException.class);
        assertThat(REQUESTS).isEmpty();
    }

    @Test
    public void compositeCollectionsWithUnparseableResponseShouldFail() {
        final Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("x");

        final Exchange exchange = template().request("direct:compositeCreateSObjectCollections",
                e -> e.getIn().setBody(List.of(merchandise)));

        assertThat(exchange.getException()).isInstanceOf(SalesforceException.class)
                .hasMessageContaining("SObject Collections API");
    }

    @Test
    public void compositeWithUnparseableResponseShouldFail() {
        final SObjectComposite composite = new SObjectComposite(SalesforceEndpointConfig.DEFAULT_VERSION, true);
        composite.addGet("Account", "001D000000IqhSLIAZ", "ref1");

        assertCompositeFails("direct:composite", composite);
    }

    @Test
    public void compositeBatchWithUnparseableResponseShouldFail() {
        final SObjectBatch batch = new SObjectBatch(SalesforceEndpointConfig.DEFAULT_VERSION);
        batch.addGet("Account", "001D000000IqhSLIAZ");

        assertCompositeFails("direct:compositeBatch", batch);
    }

    @Test
    public void compositeTreeWithUnparseableResponseShouldFail() {
        final Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("x");
        final SObjectTree tree = new SObjectTree();
        tree.addObject(merchandise);

        assertCompositeFails("direct:compositeTree", tree);
    }

    private void assertCompositeFails(String uri, Object body) {
        final Exchange exchange = template().request(uri, e -> e.getIn().setBody(body));

        assertThat(exchange.getException()).isInstanceOf(SalesforceException.class)
                .hasMessageContaining("Composite API");
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:createSObject").to("salesforce:createSObject?sObjectName=Account");

                from("direct:raw")
                        .to("salesforce:raw?format=JSON&rawMethod=POST&rawPath=/services/data/v51.0/sobjects/Account");

                from("direct:compositeCreateSObjectCollections").to("salesforce:compositeCreateSObjectCollections");

                from("direct:composite").to("salesforce:composite");

                from("direct:compositeBatch").to("salesforce:composite-batch");

                from("direct:compositeTree").to("salesforce:composite-tree");
            }
        };
    }
}
