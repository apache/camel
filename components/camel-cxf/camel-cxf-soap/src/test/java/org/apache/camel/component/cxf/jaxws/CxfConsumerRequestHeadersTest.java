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
package org.apache.camel.component.cxf.jaxws;

import java.util.ArrayList;
import java.util.List;

import javax.xml.transform.dom.DOMSource;

import org.w3c.dom.Document;

import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CxfPayload;
import org.apache.camel.component.cxf.common.message.CxfConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxp.XmlConverter;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.cxf.binding.soap.SoapHeader;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cxf consumer does not return the common HTTP request headers as HTTP response headers, and the cxf producer still
 * sends them to the remote service.
 */
public class CxfConsumerRequestHeadersTest extends CamelTestSupport {

    private static final String ECHO_REQUEST = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                                               + "<soap:Body><ns1:echo xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
                                               + "<arg0 xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">Hello World!</arg0>"
                                               + "</ns1:echo></soap:Body></soap:Envelope>";

    private static final String ECHO_RESPONSE = "<ns1:echoResponse xmlns:ns1=\"http://jaxws.cxf.component.camel.apache.org/\">"
                                                + "<return xmlns=\"http://jaxws.cxf.component.camel.apache.org/\">echo Hello World!</return>"
                                                + "</ns1:echoResponse>";

    private static final String ECHO_RESPONSE_ENVELOPE
            = "<soap:Envelope xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
              + "<soap:Body>" + ECHO_RESPONSE + "</soap:Body></soap:Envelope>";

    private static AvailablePortFinder.Port port;
    private static AvailablePortFinder.Port backendPort;

    @BindToRegistry("returnRequestHeadersBinding")
    private final DefaultCxfBinding returnRequestHeadersBinding = new DefaultCxfBinding() {
        @Override
        protected boolean isHttpRequestHeader(String name) {
            return false;
        }
    };

    @BeforeAll
    static void allocatePorts() {
        port = AvailablePortFinder.find();
        backendPort = AvailablePortFinder.find();
    }

    @AfterAll
    static void releasePorts() {
        if (port != null) {
            port.release();
        }
        if (backendPort != null) {
            backendPort.release();
        }
    }

    private String address(String path) {
        return "http://localhost:" + port.getPort() + "/" + getClass().getSimpleName() + "/" + path;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf://" + address("echo")
                     + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService&dataFormat=PAYLOAD")
                        .process(CxfConsumerRequestHeadersTest::reply);

                from("cxf://" + address("custom")
                     + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService&dataFormat=PAYLOAD"
                     + "&cxfBinding=#returnRequestHeadersBinding")
                        .process(CxfConsumerRequestHeadersTest::reply);

                from("undertow:http://localhost:" + backendPort.getPort() + "/"
                     + CxfConsumerRequestHeadersTest.class.getSimpleName() + "/backend")
                        .to("mock:backend")
                        .setHeader(Exchange.CONTENT_TYPE, constant("text/xml"))
                        .setBody(constant(ECHO_RESPONSE_ENVELOPE));
            }
        };
    }

    private static void reply(Exchange exchange) throws Exception {
        Document document = new XmlConverter().toDOMDocument(ECHO_RESPONSE, exchange);
        CxfPayload<SoapHeader> payload
                = new CxfPayload<>(null, new ArrayList<>(List.of(new DOMSource(document.getDocumentElement()))), null);
        exchange.getMessage().setBody(payload);
        exchange.getMessage().setHeader("X-Reply", "done");
    }

    private static HttpPost echoRequest(String uri) {
        HttpPost post = new HttpPost(uri);
        post.addHeader("Authorization", "Basic dXNlcjpwYXNz");
        // lower case, as with HTTP/2
        post.addHeader("cookie", "session=1234");
        post.addHeader("User-Agent", "test-client");
        post.addHeader("Accept", "text/xml");
        post.addHeader("X-Caller", "caller");
        post.setEntity(new StringEntity(ECHO_REQUEST, ContentType.create("text/xml", "UTF-8")));
        return post;
    }

    @Test
    public void testRequestHeadersNotReturned() throws Exception {
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            httpclient.execute(echoRequest(address("echo")), response -> {
                assertEquals(200, response.getCode());
                String body = EntityUtils.toString(response.getEntity());
                assertTrue(body.contains("echo Hello World!"), "Get a wrong response: " + body);

                assertNull(response.getFirstHeader("Authorization"), "Authorization should not be returned");
                assertNull(response.getFirstHeader("Cookie"), "Cookie should not be returned");
                assertNull(response.getFirstHeader("User-Agent"), "User-Agent should not be returned");
                assertNull(response.getFirstHeader("Accept"), "Accept should not be returned");
                assertNull(response.getFirstHeader("Host"), "Host should not be returned");
                assertHeader(response, "X-Reply", "done");
                return null;
            });
        }
    }

    @Test
    public void testRequestHeadersReturnedWithCustomBinding() throws Exception {
        try (CloseableHttpClient httpclient = HttpClientBuilder.create().build()) {
            httpclient.execute(echoRequest(address("custom")), response -> {
                assertEquals(200, response.getCode());
                assertHeader(response, "Authorization", "Basic dXNlcjpwYXNz");
                assertHeader(response, "User-Agent", "test-client");
                assertHeader(response, "X-Reply", "done");
                return null;
            });
        }
    }

    @Test
    public void testProducerSendsRequestHeaders() throws Exception {
        MockEndpoint backend = getMockEndpoint("mock:backend");
        backend.expectedMessageCount(1);
        backend.expectedHeaderReceived("Authorization", "Basic dXNlcjpwYXNz");
        backend.expectedHeaderReceived("User-Agent", "test-client");
        backend.expectedHeaderReceived("X-Caller", "caller");

        Exchange exchange = template.send("cxf://http://localhost:" + backendPort.getPort() + "/"
                                          + getClass().getSimpleName() + "/backend"
                                          + "?serviceClass=org.apache.camel.component.cxf.jaxws.HelloService",
                e -> {
                    e.getIn().setBody(new ArrayList<>(List.of("Hello World!")));
                    e.getIn().setHeader(CxfConstants.OPERATION_NAME, "echo");
                    e.getIn().setHeader("Authorization", "Basic dXNlcjpwYXNz");
                    e.getIn().setHeader("User-Agent", "test-client");
                    e.getIn().setHeader("X-Caller", "caller");
                });

        MockEndpoint.assertIsSatisfied(context);
        assertNull(exchange.getException());
        assertEquals("echo Hello World!", exchange.getMessage().getBody(String.class));
    }

    private static void assertHeader(ClassicHttpResponse response, String name, String expected) {
        assertNotNull(response.getFirstHeader(name), name + " should be returned");
        assertEquals(expected, response.getFirstHeader(name).getValue());
    }
}
