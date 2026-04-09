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

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * CAMEL-23249: CXF RS Rest service loses content-type with stream caching enabled.
 *
 * When a CXF RS consumer route returns a JAX-RS {@link Response} with an explicit content type set via
 * {@code Response.type(MediaType)}, and stream caching is enabled (the default in Camel 4.x), the content-type header
 * is lost because {@code CxfConverter.toStreamCache(Response, Exchange)} only preserves the HTTP status code but not
 * the response metadata headers.
 *
 * @see <a href="https://issues.apache.org/jira/browse/CAMEL-23249">CAMEL-23249</a>
 * @see <a href="https://github.com/driseley/cxf-responsecaching-test">Reproducer project</a>
 */
public class CxfRsStreamCacheContentTypeTest extends CamelTestSupport {

    private static final String PUT_REQUEST = "<Customer><name>Mary</name><id>123</id></Customer>";
    private static final String JSON_RESPONSE = "{\"name\":\"Mary\",\"id\":123}";
    private static final String CONTEXT = "/CxfRsStreamCacheContentTypeTest";
    private static final int PORT = CXFTestSupport.getPort5();
    private static final String CXT = PORT + CONTEXT;

    private static final String CXF_RS_ENDPOINT_URI
            = "cxfrs://http://localhost:" + CXT
              + "/rest?resourceClasses=org.apache.camel.component.cxf.jaxrs.testbean.CustomerService";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(CXF_RS_ENDPOINT_URI)
                        .process(exchange -> {
                            // Return a Response with explicit JSON content type and a custom header,
                            // mimicking the reproducer's EchoServiceImpl.echoVariable()
                            Response resp = Response
                                    .status(Response.Status.OK)
                                    .type(MediaType.APPLICATION_JSON)
                                    .header("X-Custom-Header", "custom-value")
                                    .entity(JSON_RESPONSE)
                                    .build();
                            exchange.getMessage().setBody(resp);
                        })
                        // The log() EIP triggers stream cache conversion of the Response body.
                        // After this point the body is a StreamCache, not a Response.
                        .log("Response body: ${body}");
            }
        };
    }

    /**
     * Verifies that the content-type set via {@code Response.type(MediaType.APPLICATION_JSON)} is preserved in the HTTP
     * response when stream caching converts the {@link Response} to a {@code StreamCache}.
     *
     * This test reproduces the exact scenario from CAMEL-23249: a CXF RS consumer route returns a JAX-RS Response with
     * an explicit content type, but the client receives "application/octet-stream" instead.
     */
    @Test
    public void testContentTypePreservedAfterStreamCacheConversion() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + CXT + "/rest/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            httpclient.execute(put, response -> {
                assertEquals(200, response.getCode(), "HTTP status should be 200");

                assertNotNull(response.getFirstHeader("Content-Type"),
                        "Content-Type header should be present in response");
                String contentType = response.getFirstHeader("Content-Type").getValue();
                assertEquals("application/json", contentType,
                        "Content-Type should be application/json but was: " + contentType);

                String body = EntityUtils.toString(response.getEntity());
                assertEquals(JSON_RESPONSE, body);

                return null;
            });
        }
    }

    /**
     * Verifies that custom response headers set via {@code Response.header(name, value)} are also preserved when stream
     * caching converts the {@link Response} to a {@code StreamCache}.
     */
    @Test
    public void testCustomHeaderPreservedAfterStreamCacheConversion() throws Exception {
        HttpPut put = new HttpPut("http://localhost:" + CXT + "/rest/customerservice/customers");
        StringEntity entity = new StringEntity(PUT_REQUEST, ContentType.parse("text/xml; charset=ISO-8859-1"));
        put.setEntity(entity);

        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            httpclient.execute(put, response -> {
                assertEquals(200, response.getCode(), "HTTP status should be 200");

                assertNotNull(response.getFirstHeader("X-Custom-Header"),
                        "X-Custom-Header should be present in response");
                assertEquals("custom-value", response.getFirstHeader("X-Custom-Header").getValue(),
                        "Custom header value should be preserved after stream cache conversion");

                return null;
            });
        }
    }
}
