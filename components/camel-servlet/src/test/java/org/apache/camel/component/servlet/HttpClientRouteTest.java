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
package org.apache.camel.component.servlet;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.camel.Exchange;
import org.apache.camel.FailedToStartRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

public class HttpClientRouteTest extends ServletCamelRouterTestSupport {

    private static final String POST_DATA = "<request> hello world </request>";
    private static final String CONTENT_TYPE = "text/xml; charset=UTF-8";
    private static final String UNICODE_TEXT = "B\u00FCe W\u00F6rld";

    @Test
    public void testHttpClient() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/hello",
                new ByteArrayInputStream(POST_DATA.getBytes()), CONTENT_TYPE);
        WebResponse response = query(req);

        assertEquals("text/xml", response.getContentType(), "Get wrong content type");
        assertTrue("UTF-8".equalsIgnoreCase(response.getCharacterSet()));
        assertEquals("/hello", response.getHeaderField("PATH"), "Get a wrong message header");
        assertEquals("OK", response.getResponseMessage(), "The response message is wrong");

        req = new PostMethodWebRequest(
                contextUrl + "/services/helloworld",
                new ByteArrayInputStream(POST_DATA.getBytes()), CONTENT_TYPE);
        response = query(req);

        assertEquals("text/xml", response.getContentType(), "Get wrong content type");
        assertTrue("UTF-8".equalsIgnoreCase(response.getCharacterSet()));
        assertEquals("/helloworld", response.getHeaderField("PATH"), "Get a wrong message header");
        assertEquals("OK", response.getResponseMessage(), "The response message is wrong");
    }

    @Test
    public void testHttpRestricMethod() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/testHttpMethodRestrict",
                new ByteArrayInputStream(POST_DATA.getBytes()), "text/xml; charset=UTF-8");
        WebResponse response = query(req);
        assertEquals("OK", response.getResponseMessage(), "The response message is wrong");
        assertEquals(POST_DATA, response.getText(), "The response body is wrong");

        // Send other web method request
        req = new GetMethodWebRequest(contextUrl + "/services/testHttpMethodRestrict");
        try {
            response = query(req);
            fail("Expect the exception here");
        } catch (Exception ex) {
            HttpException httpException = (HttpException) ex;
            assertEquals(405, httpException.getResponseCode(), "Get a wrong response code");
        }
    }

    @Test
    public void testHttpConverter() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/testConverter",
                new ByteArrayInputStream(POST_DATA.getBytes()), "text/xml; charset=UTF-8");
        WebResponse response = query(req, false);
        assertEquals("OK", response.getResponseMessage(), "The response message is wrong");
        assertEquals("Bye World", response.getText(), "The response body is wrong");
    }

    @Test
    public void testHttpUnicodeResponseWithStringResponse() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/testUnicodeWithStringResponse",
                new ByteArrayInputStream(POST_DATA.getBytes()), "text/xml; charset=UTF-8");
        WebResponse response = query(req, false);
        assertEquals("OK", response.getResponseMessage(), "The response message is wrong");
        assertEquals(UNICODE_TEXT, response.getText(StandardCharsets.UTF_8), "The response body is wrong");
    }

    @Test
    public void testHttpUnicodeResponseWithObjectResponse() throws Exception {
        WebRequest req = new PostMethodWebRequest(
                contextUrl + "/services/testUnicodeWithObjectResponse",
                new ByteArrayInputStream(POST_DATA.getBytes()), "text/xml; charset=UTF-8");
        WebResponse response = query(req, false);
        assertEquals("OK", response.getResponseMessage(), "The response message is wrong");
        assertEquals(UNICODE_TEXT, response.getText(StandardCharsets.UTF_8), "The response body is wrong");
    }

    @Test
    public void testCreateSerlvetEndpointProducer() throws Exception {
        assumeTrue(startCamelContext, "don't test it with web.xml configure");
        try {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from("direct:start").to("servlet:///testworld");
                }
            });
            fail("Excepts exception here");
        } catch (Exception ex) {
            assertTrue(ex instanceof FailedToStartRouteException, "Get a wrong exception.");
            assertTrue(ex.getCause().getCause() instanceof UnsupportedOperationException,
                    "Get a wrong cause of exception.");
        }
    }

    public static class MyServletRoute extends RouteBuilder {

        @Override
        public void configure() throws Exception {
            errorHandler(noErrorHandler());
            // START SNIPPET: route
            from("servlet:hello?matchOnUriPrefix=true").process(exchange -> {
                String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                String path = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                path = path.substring(path.lastIndexOf('/'));

                assertEquals(CONTENT_TYPE, contentType, "Get a wrong content type");
                // assert camel http header
                String charsetEncoding = exchange.getIn().getHeader(Exchange.HTTP_CHARACTER_ENCODING, String.class);
                assertEquals("UTF-8", charsetEncoding, "Get a wrong charset name from the message header");
                // assert exchange charset
                assertEquals("UTF-8", exchange.getProperty(Exchange.CHARSET_NAME),
                        "Get a wrong charset naem from the exchange property");
                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentType + "; charset=UTF-8");
                exchange.getMessage().setHeader("PATH", path);
                exchange.getMessage().setBody("<b>Hello World</b>");
            });
            // END SNIPPET: route

            from("servlet:testHttpMethodRestrict?httpMethodRestrict=POST").process(exchange -> {
                String request = exchange.getIn().getBody(String.class);
                exchange.getMessage().setBody(request);
            });

            from("servlet:testConverter?matchOnUriPrefix=true")
                    .convertBodyTo(String.class)
                    .process(exchange -> {
                        HttpServletRequest request = exchange.getIn(HttpServletRequest.class);
                        assertNotNull(request, "We should get request object here");
                        HttpServletResponse response = exchange.getIn(HttpServletResponse.class);
                        assertNotNull(response, "We should get response object here");
                        String s = exchange.getIn().getBody(String.class);
                        assertEquals("<request> hello world </request>", s);
                    }).transform(constant("Bye World"));

            from("servlet:testUnicodeWithStringResponse?matchOnUriPrefix=true")
                    .process(exchange -> {
                        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentType + "; charset=UTF-8");
                    })
                    .transform(constant(UNICODE_TEXT));

            from("servlet:testUnicodeWithObjectResponse?matchOnUriPrefix=true")
                    .process(exchange -> {
                        String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                        exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, contentType + "; charset=UTF-8");
                    })
                    .transform(constant(new Object() {
                        @Override
                        public String toString() {
                            return UNICODE_TEXT;
                        }
                    }));
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new MyServletRoute();
    }

}
