/**
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
package org.apache.camel.component.http4;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.http4.handler.BasicValidationHandler;
import org.apache.http.HttpStatus;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @version 
 */
public class HttpMethodsTest extends BaseHttpTest {

    private HttpServer localServer;
    
    @Before
    @Override
    public void setUp() throws Exception {
        localServer = ServerBootstrap.bootstrap().
                setHttpProcessor(getBasicHttpProcessor()).
                setConnectionReuseStrategy(getConnectionReuseStrategy()).
                setResponseFactory(getHttpResponseFactory()).
                setExpectationVerifier(getHttpExpectationVerifier()).
                setSslContext(getSSLContext()).
                registerHandler("/get", new BasicValidationHandler("GET", null, null, getExpectedContent())).
                registerHandler("/patch", new BasicValidationHandler("PATCH", null, null, getExpectedContent())).
                registerHandler("/patch1", new BasicValidationHandler("PATCH", null, "rocks camel?", getExpectedContent())).
                registerHandler("/post", new BasicValidationHandler("POST", null, null, getExpectedContent())).
                registerHandler("/post1", new BasicValidationHandler("POST", null, "rocks camel?", getExpectedContent())).
                registerHandler("/put", new BasicValidationHandler("PUT", null, null, getExpectedContent())).
                registerHandler("/trace", new BasicValidationHandler("TRACE", null, null, getExpectedContent())).
                registerHandler("/options", new BasicValidationHandler("OPTIONS", null, null, getExpectedContent())).
                registerHandler("/delete", new BasicValidationHandler("DELETE", null, null, getExpectedContent())).
                registerHandler("/delete1", new BasicValidationHandler("DELETE", null, null, getExpectedContent())).
                registerHandler("/head", new BasicValidationHandler("HEAD", null, null, getExpectedContent())).create();
        localServer.start();

        super.setUp();
    }

    @After
    @Override
    public void tearDown() throws Exception {
        super.tearDown();

        if (localServer != null) {
            localServer.stop();
        }
    }
    
    @Test
    public void httpGet() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/get", new Processor() {
            public void process(Exchange exchange) throws Exception {
            }
        });

        assertExchange(exchange);
    }
    
    @Test
    public void httpGetWithUriParam() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/get?httpMethod=GET", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPatch() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/patch?throwExceptionOnFailure=false", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PATCH");
            }
        });

        assertNotNull(exchange);
        assertTrue(exchange.hasOut());

        Message out = exchange.getOut();
        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_OK, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("OK", headers.get(Exchange.HTTP_RESPONSE_TEXT));
        assertEquals("12", headers.get("Content-Length"));
        assertNotNull("Should have Content-Type header", headers.get("Content-Type"));
        assertEquals("camel rocks!", out.getBody(String.class));
    }

    @Test
    public void httpPatchWithBody() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/patch1?throwExceptionOnFailure=false", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("rocks camel?");
            }
        });

        assertNotNull(exchange);
        assertTrue(exchange.hasOut());

        Message out = exchange.getOut();
        Map<String, Object> headers = out.getHeaders();
        assertEquals(HttpStatus.SC_METHOD_FAILURE, headers.get(Exchange.HTTP_RESPONSE_CODE));
        assertEquals("0", headers.get("Content-Length"));
        assertEquals("", out.getBody(String.class));
    }

    @Test
    public void httpPost() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/post", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "POST");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPostWithBody() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/post1", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("rocks camel?");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpPut() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/put", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "PUT");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpTrace() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/trace", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "TRACE");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpOptions() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/options", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "OPTIONS");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpDelete() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/delete", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "DELETE");
            }
        });

        assertExchange(exchange);
    }

    @Test
    public void httpDeleteWithBody() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/delete1?deleteWithBody=true", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "DELETE");
                exchange.getIn().setBody("rocks camel?");
            }
        });

        assertExchange(exchange);

        // the http4 server will not provide body on HTTP DELETE so we cannot test the server side
    }

    @Test
    public void httpHead() throws Exception {

        Exchange exchange = template.request("http4://" + localServer.getInetAddress().getHostName() + ":" + localServer.getLocalPort() + "/head", new Processor() {
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setHeader(Exchange.HTTP_METHOD, "HEAD");
            }
        });

        assertNotNull(exchange);

        Message out = exchange.getOut();
        assertNotNull(out);
        assertHeaders(out.getHeaders());
        assertNull(out.getBody(String.class));
    }

}