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

import jakarta.ws.rs.core.Response;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.component.cxf.jaxrs.response.MyResponse;
import org.apache.camel.component.cxf.jaxrs.testbean.Customer;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class CxfRsConvertBodyToTest extends CamelTestSupport {
    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String CXT = CXFTestSupport.getPort1() + "/CxfRsConvertBodyToTest";
    private static final String CXF_RS_ENDPOINT_URI
            = "cxfrs://http://localhost:" + CXT
              + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

    // Define a separate endpoint for the status code test to avoid interference
    private static final String CXF_RS_STATUS_TEST_URI
            = "cxfrs://http://localhost:" + CXFTestSupport.getPort2()
              + "/reststatus?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

    private static final String CXF_RS_STATUS_TEST_URL
            = "http://localhost:" + CXFTestSupport.getPort2() + "/reststatus/customerservice/customers";

    private static final String CXF_RS_EMPTY_BODY_TEST_URI
            = "cxfrs://http://localhost:" + CXFTestSupport.getPort3()
              + "/restempty?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

    private static final String CXF_RS_EMPTY_BODY_TEST_URL
            = "http://localhost:" + CXFTestSupport.getPort3() + "/restempty/customerservice/customers";

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                Response ok = Response.ok().build();

                from(CXF_RS_ENDPOINT_URI)
                        // should be able to convert to Customer
                        .convertBodyTo(Customer.class)
                        .to("mock:result")
                        // respond with OK (status 200)
                        .transform(constant(ok));

                // New Route: Intentionally uses an EIP (.log()) after setting the JAX-RS Response
                // to trigger the StreamCache conversion.
                from(CXF_RS_STATUS_TEST_URI)
                        .to("mock:statusCheckResult")
                        // Set Response object with custom status 202 inside a processor
                        .process(exchange -> {
                            // Simulate setting the Response object
                            Response resp = Response.status(202).entity("").build();
                            exchange.getMessage().setBody(resp);
                        })
                        // The .log() EIP here is the one that triggered the StreamCache
                        .log("Checking if status is still 202 after logging.");

                // Route for testing empty body scenario (No Entity)
                from(CXF_RS_EMPTY_BODY_TEST_URI)
                        .to("mock:emptyBodyResult")
                        .process(exchange -> {
                            // Creating a response with NO entity
                            Response resp = Response.status(204).build();
                            exchange.getMessage().setBody(resp);
                        })
                        .log("Logging to trigger conversion with empty body");

            }
        };
    }

    @Test
    public void testPutConsumer() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(Customer.class);

        HttpPut put = new HttpPut("http://localhost:" + CXT + "/rest/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.addHeader("test", "header1;header2");
        put.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            MyResponse httpResponse = httpclient.execute(put, response -> {
                return new MyResponse(response.getCode(), EntityUtils.toString(response.getEntity()));
            });
            assertEquals(200, httpResponse.status());
            assertEquals("", httpResponse.content());
        }

        mock.assertIsSatisfied();
    }

    @Test
    public void testResponseStatusPreservedAfterConversion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:statusCheckResult");
        mock.expectedMessageCount(1);

        HttpPut put = new HttpPut(CXF_RS_STATUS_TEST_URL);
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.setEntity(entity);

        int expectedStatus = 202;

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            MyResponse httpResponse = httpclient.execute(put, response -> {
                return new MyResponse(response.getCode(), EntityUtils.toString(response.getEntity()));
            });
            assertEquals(expectedStatus, httpResponse.status(),
                    "The HTTP status code should be 202");

            String responseBody = httpResponse.content();
            assertNotNull(responseBody);
            assertEquals("", responseBody);
        }

        mock.assertIsSatisfied();
    }

    @Test
    public void testEmptyResponseStatusPreservedAfterConversion() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:emptyBodyResult");
        mock.expectedMessageCount(1);

        HttpPut put = new HttpPut(CXF_RS_EMPTY_BODY_TEST_URL);
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            MyResponse httpResponse = httpclient.execute(put, response -> {
                HttpEntity et = response.getEntity();
                return new MyResponse(response.getCode(), et == null ? null : EntityUtils.toString(response.getEntity()));
            });
            // Verify status 204 is preserved even with no entity body
            assertEquals(204, httpResponse.status(), "Status 204 should be preserved for empty response");

            // Entity should be null or empty
            assertNull(httpResponse.content());
        }

        mock.assertIsSatisfied();
    }

}
