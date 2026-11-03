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
package org.apache.camel.component.cxf.jaxrs;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.jaxrs.response.MyResponse;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * With the Default binding a route that does not set a reply body replies with the request entity instead of failing on
 * the request MessageContentsList.
 */
public class CxfRsConsumerUnchangedBodyTest extends CamelTestSupport {
    @RegisterExtension
    static final AvailablePortFinder.Port PORT = AvailablePortFinder.find();

    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String TEST_URL
            = "http://localhost:" + PORT.getPort() + "/CxfRsConsumerUnchangedBodyTest/customerservice/customers";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxfrs://http://localhost:" + PORT.getPort() + "/CxfRsConsumerUnchangedBodyTest"
                     + "?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService")
                        // the route does not change the body
                        .log("${header.operationName}")
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testUnchangedEntityIsTheReply() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        HttpPut put = new HttpPut(TEST_URL);
        put.setEntity(new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1")));
        put.addHeader("Accept", "text/xml");

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            MyResponse httpResponse = httpclient.execute(put,
                    response -> new MyResponse(response.getCode(), EntityUtils.toString(response.getEntity())));
            assertEquals(200, httpResponse.status(), httpResponse.toString());
            assertTrue(httpResponse.content().contains("<name>Mary</name>"), httpResponse.content());
            assertTrue(httpResponse.content().contains("<id>123</id>"), httpResponse.content());
        }

        mock.assertIsSatisfied();
    }

    @Test
    public void testUnchangedEmptyParametersOfCollectionMethod() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // getCustomers() returns a List, so the empty MessageContentsList is written as an empty collection
        HttpGet get = new HttpGet(TEST_URL);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            MyResponse httpResponse = httpclient.execute(get,
                    response -> new MyResponse(response.getCode(), EntityUtils.toString(response.getEntity())));
            assertEquals(200, httpResponse.status(), httpResponse.toString());
            assertTrue(httpResponse.content().contains("<Customers></Customers>"), httpResponse.content());
        }

        mock.assertIsSatisfied();
    }
}
