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
package org.apache.camel.component.cxf;

import jakarta.servlet.http.HttpServletResponse;

import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.HttpString;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.cxf.common.CXFTestSupport;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.apache.camel.util.IOHelper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CxfPayloadRouterContentLengthTest extends CamelSpringTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(CxfPayloadRouterContentLengthTest.class);

    /*
     * The response message is generated directly. The issue here is that the
     * xsi and xs namespaces are defined on the SOAP envelope but are used
     * within the payload. This can cause issues with some type conversions in
     * PAYLOAD mode, as the Camel-CXF endpoint will return some kind of window
     * within the StAX parsing (and the namespace definitions are outside).
     *
     * If some CXF implementation bean is used as the service the namespaces
     * will be defined within the payload (and everything works fine).
     */
    private static final String RESPONSE_STRING = "This is the response string";
    private static final String RESPONSE_MESSAGE = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                                                   + "<ns0:payload xmlns:ns0=\"http://schema.apache.org/test\"><ns0:response>"
                                                   + RESPONSE_STRING + "</ns0:response></ns0:payload>"
                                                   + "</s:Body></s:Envelope>";
    private static final String REQUEST_MESSAGE = "<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\"><s:Body>"
                                                  + "<ns0:payload xmlns:ns0=\"http://schema.apache.org/test\"><ns0:request>foo</ns0:request></ns0:payload>"
                                                  + "</s:Body></s:Envelope>";

    // The Camel-Test with CXF will re-use undertow instances, so the ports1 to 6 are already blocked
    private static final int UNDERTOW_PORT = AvailablePortFinder.getNextAvailable();

    private Undertow server;

    static {
        System.setProperty("CXFTestSupport.undertowPort", Integer.toString(UNDERTOW_PORT));
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        /*
         * We start a undertow for the service in order to have better control over
         * the response. The response must contain only a Content-Type and a
         * Content-Length but no other header
         */
        LOG.info("Starting undertow server at port {}", UNDERTOW_PORT);
        server = Undertow.builder()
                .addHttpListener(UNDERTOW_PORT, "localhost")
                .setHandler(new HttpHandler() {
                    @Override
                    public void handleRequest(HttpServerExchange httpServerExchange) throws Exception {
                        httpServerExchange.getResponseHeaders().put(new HttpString("Content-Type"), "text/html");
                        // the Content-Length is correct for this response message
                        httpServerExchange.getResponseHeaders().put(new HttpString("Content-Length"),
                                RESPONSE_MESSAGE.length());
                        httpServerExchange.setResponseContentLength(RESPONSE_MESSAGE.length());
                        httpServerExchange.setStatusCode(HttpServletResponse.SC_OK);

                        httpServerExchange.getResponseSender().send(RESPONSE_MESSAGE);
                    }
                })
                .build();

        server.start();
        // Load the CXF endpoints for the route
        LOG.info("Start Routing Scenario at port {}", CXFTestSupport.getPort1());
        super.setUp();
    }

    @Override
    @AfterEach
    public void tearDown() throws Exception {
        // close the spring context
        IOHelper.close(applicationContext);
        // stop the undertow server
        if (server != null) {
            server.stop();
        }
        super.tearDown();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("cxf:bean:proxyEndpoint?dataFormat=PAYLOAD") //
                        .removeHeaders(".*")
                        // call an external Web service in payload mode
                        .to("cxf:bean:serviceEndpoint?dataFormat=PAYLOAD");
            }
        };
    }

    @Test
    public void testInvokeRouter() throws Exception {
        CloseableHttpClient httpclient = HttpClients.createDefault();
        long contentLength = 0;
        boolean isChunked = false;
        String receivedContent = null;
        HttpPost httppost = new HttpPost("http://localhost:" + CXFTestSupport.getPort1() + "/TEST/PROXY");
        StringEntity reqEntity = new StringEntity(REQUEST_MESSAGE, ContentType.TEXT_XML, false);
        httppost.setEntity(reqEntity);
        try (httpclient; CloseableHttpResponse response = httpclient.execute(httppost)) {
            HttpEntity respEntity = response.getEntity();
            contentLength = respEntity.getContentLength();
            isChunked = respEntity.isChunked();
            receivedContent = EntityUtils.toString(respEntity);
            EntityUtils.consume(response.getEntity());
        }
        assertNotNull(receivedContent);
        // chunked encoding is fine, we don't need to check the content length
        if (!isChunked) {
            assertEquals(receivedContent.length(), contentLength);
        }
        assertTrue(receivedContent.contains(RESPONSE_STRING),
                "[" + receivedContent + "] does not contain [" + RESPONSE_STRING + "]");
        // check whether the response was cut off by the client because the
        // Content-Length was wrong
        assertTrue(receivedContent.matches(".*</.*:Envelope>"),
                "[" + receivedContent + "] does not contain the closing Envelope tag.");
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/cxf/CxfPayloadRouterContentLengthBeans.xml");
    }
}
