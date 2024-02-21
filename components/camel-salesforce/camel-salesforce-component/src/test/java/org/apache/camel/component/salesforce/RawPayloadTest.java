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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.params.Parameter;
import org.apache.camel.test.junit5.params.Parameterized;
import org.apache.camel.test.junit5.params.Parameters;
import org.apache.camel.test.junit5.params.Test;
import org.eclipse.jetty.http.HttpHeader;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Tag("standalone")
@Parameterized
public class RawPayloadTest extends AbstractSalesforceTestBase {

    @Parameter
    public static String format;

    @Parameter(1)
    public static String endpointUri;

    private static final String OAUTH2_TOKEN_PATH = "/services/oauth2/token";
    private static final String XML_RESPONSE = "<response/>";
    private static final String JSON_RESPONSE = "{ \"response\" : \"mock\" }";

    private static HttpUrl loginUrl;
    private static MockWebServer server;

    private static String lastFormat;
    private static String expectedResponse;
    private static String requestBody;
    private static Map<String, Object> headers;

    @Override
    protected void createComponent() throws Exception {

        // create the component
        SalesforceComponent component = new SalesforceComponent();
        final SalesforceEndpointConfig config = new SalesforceEndpointConfig();
        config.setApiVersion(System.getProperty("apiVersion", SalesforceEndpointConfig.DEFAULT_VERSION));
        component.setConfig(config);

        SalesforceLoginConfig dummyLoginConfig = new SalesforceLoginConfig();
        dummyLoginConfig.setClientId("ignored");
        dummyLoginConfig.setClientSecret("ignored");
        dummyLoginConfig.setRefreshToken("ignored");
        dummyLoginConfig.setLoginUrl(loginUrl.toString());
        component.setLoginConfig(dummyLoginConfig);

        // add it to context
        context().addComponent("salesforce", component);
    }

    @AfterAll
    public static void shutDownServer() throws IOException {
        // shutdown mock server
        if (server != null) {
            server.shutdown();
        }
    }

    @BeforeAll
    public static void startServer() throws IOException {

        // create mock server
        server = new MockWebServer();

        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest recordedRequest) throws InterruptedException {
                if (recordedRequest.getPath().equals(OAUTH2_TOKEN_PATH)) {
                    return new MockResponse().setResponseCode(200)
                            .setBody(
                                    "{ \"access_token\": \"mock_token\", \"id\": \"https://login.salesforce.com/id/00D4100000xxxxxxxx/0054100000xxxxxxxx\", \"instance_url\": \""
                                     + loginUrl + "\"}");
                } else {
                    return new MockResponse().setResponseCode(200)
                            .setHeader(HttpHeader.CONTENT_TYPE.toString(),
                                    recordedRequest.getHeader(HttpHeader.CONTENT_TYPE.toString()))
                            .setBody("XML".equals(format) ? XML_RESPONSE : JSON_RESPONSE);
                }
            }
        });

        // start the server
        server.start();
        loginUrl = server.url("");
    }

    @BeforeEach
    public void setupRequestResponse() {
        if (!format.equals(lastFormat)) {
            // expected response and test request
            final boolean isXml = "XML".equals(format);
            expectedResponse = isXml ? XML_RESPONSE : JSON_RESPONSE;
            if (isXml) {
                requestBody = "<request/>";
            } else {
                requestBody = "{ \"request\" : \"mock\" }";
            }
            headers = new HashMap<>();
            headers.put("sObjectId", "mockId");
            headers.put("sObjectIdValue", "mockIdValue");
            headers.put("id", "mockId");
            headers.put(SalesforceEndpointConfig.APEX_QUERY_PARAM_PREFIX + "id", "mockId");

            lastFormat = format;
        }
    }

    @Test
    public void testRestApi() throws Exception {
        final String responseBody = template().requestBodyAndHeaders(endpointUri, requestBody, headers, String.class);
        assertNotNull(responseBody, "Null response for endpoint " + endpointUri);
        assertEquals(expectedResponse, responseBody, "Unexpected response for endpoint " + endpointUri);
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {

        // create test route
        return new RouteBuilder() {
            @Override
            public void configure() {

                // testGetVersion
                from("direct:getVersions").to("salesforce:getVersions?rawPayload=true&format=" + format);

                // testGetResources
                from("direct:getResources").to("salesforce:getResources?rawPayload=true&format=" + format);

                // testGetGlobalObjects
                from("direct:getGlobalObjects").to("salesforce:getGlobalObjects?rawPayload=true&format=" + format);

                // testGetBasicInfo
                from("direct:getBasicInfo")
                        .to("salesforce:getBasicInfo?sObjectName=Merchandise__c&rawPayload=true&format=" + format);

                // testGetDescription
                from("direct:getDescription")
                        .to("salesforce:getDescription?sObjectName=Merchandise__c&rawPayload=true&format=" + format);

                // testGetSObject
                from("direct:getSObject").to(
                        "salesforce:getSObject?sObjectName=Merchandise__c&sObjectFields=Description__c,Price__c&rawPayload=true&format="
                                             + format);

                // testCreateSObject
                from("direct:createSObject")
                        .to("salesforce:createSObject?sObjectName=Merchandise__c&rawPayload=true&format=" + format);

                // testUpdateSObject
                from("direct:updateSObject")
                        .to("salesforce:updateSObject?sObjectName=Merchandise__c&rawPayload=true&format=" + format);

                // testDeleteSObject
                from("direct:deleteSObject")
                        .to("salesforce:deleteSObject?sObjectName=Merchandise__c&rawPayload=true&format=" + format);

                // testGetSObjectWithId
                from("direct:getSObjectWithId")
                        .to("salesforce:getSObjectWithId?sObjectName=Line_Item__c&sObjectIdName=Name&rawPayload=true&format="
                            + format);

                // testUpsertSObject
                from("direct:upsertSObject")
                        .to("salesforce:upsertSObject?sObjectName=Line_Item__c&sObjectIdName=Name&rawPayload=true&format="
                            + format);

                // testDeleteSObjectWithId
                from("direct:deleteSObjectWithId")
                        .to("salesforce:deleteSObjectWithId?sObjectName=Line_Item__c&sObjectIdName=Name&rawPayload=true&format="
                            + format);

                // testGetBlobField
                from("direct:getBlobField")
                        .to("salesforce:getBlobField?sObjectName=Document&sObjectBlobFieldName=Body&rawPayload=true&format="
                            + format);

                // testQuery
                from("direct:query")
                        .to("salesforce:query?sObjectQuery=SELECT name from Line_Item__c&rawPayload=true&format=" + format);

                // testQueryAll
                from("direct:queryAll")
                        .to("salesforce:queryAll?sObjectQuery=SELECT name from Line_Item__c&rawPayload=true&format=" + format);

                // testSearch
                from("direct:search").to("salesforce:search?sObjectSearch=FIND {Wee}&rawPayload=true&format=" + format);

                // testApexCall
                from("direct:apexCallGet").to(
                        "salesforce:apexCall?apexMethod=GET&apexUrl=Merchandise/{id}&sObjectName=Merchandise__c&rawPayload=true&format="
                                              + format);

                from("direct:apexCallGetWithId")
                        .to("salesforce:apexCall/Merchandise/?apexMethod=GET&id=dummyId&rawPayload=true&format=" + format);

                from("direct:apexCallPatch")
                        .to("salesforce:apexCall/Merchandise/?rawPayload=true&format=" + format + "&apexMethod=PATCH");

                // testComposite (only JSON format is supported)
                if (format.equalsIgnoreCase("json")) {
                    from("direct:composite").to(
                            "salesforce:composite?rawPayload=true&format=" + format
                                                + "&sObjectName=foo&sObjectIdName=bar&compositeMethod=PATCH");
                }
            }
        };
    }

    @Parameters(name = "format = {0}, endpoint = {1}")
    public static List<String[]> parameters() {
        final String[] endpoints = {
                "direct:getVersions", "direct:getResources", "direct:getGlobalObjects", "direct:getBasicInfo",
                "direct:getDescription", "direct:getSObject",
                "direct:createSObject", "direct:updateSObject", "direct:deleteSObject", "direct:getSObjectWithId",
                "direct:upsertSObject",
                "direct:deleteSObjectWithId", "direct:getBlobField", "direct:query", "direct:queryAll", "direct:search",
                "direct:apexCallGet", "direct:apexCallGetWithId", "direct:apexCallPatch",
                "direct:composite" };

        final String[] formats = { "XML", "JSON" };

        return Stream.of(formats).flatMap(f -> Stream.of(endpoints)
                .map(e -> new String[] { f, e }))
                .filter(strings -> !(strings[0].equals("XML") && strings[1].equals("direct:composite")))
                .collect(Collectors.toList());
    }
}
