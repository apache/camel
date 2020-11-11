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
package org.apache.camel.component.jetty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpSession;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.stream.InputStreamCache;
import org.apache.camel.http.common.HttpMessage;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class HttpRouteTest extends BaseJettyTest {

    protected static final String POST_MESSAGE = "<?xml version=\"1.0\" encoding=\"UTF-8\"?> " + "<test>Hello World</test>";

    private static final Logger LOG = LoggerFactory.getLogger(HttpRouteTest.class);

    protected String expectedBody = "<hello>world!</hello>";

    private int port1;
    private int port2;
    private int port3;
    private int port4;

    @Test
    public void testEndpoint() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:a");
        mockEndpoint.expectedBodiesReceived(expectedBody);

        invokeHttpEndpoint();

        mockEndpoint.assertIsSatisfied();
        List<Exchange> list = mockEndpoint.getReceivedExchanges();
        Exchange exchange = list.get(0);
        assertNotNull(exchange, "exchange");

        Message in = exchange.getIn();
        assertNotNull(in, "in");

        Map<String, Object> headers = in.getHeaders();

        LOG.info("Headers: " + headers);

        assertTrue(headers.size() > 0, "Should be more than one header but was: " + headers);
    }

    @Test
    public void testHelloEndpoint() throws Exception {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        InputStream is = new URL("http://localhost:" + port2 + "/hello").openStream();
        int c;
        while ((c = is.read()) >= 0) {
            os.write(c);
        }

        String data = new String(os.toByteArray());
        assertEquals("<b>Hello World</b>", data);
    }

    @Test
    public void testEchoEndpoint() throws Exception {
        String out = template.requestBody("http://localhost:" + port1 + "/echo", "HelloWorld", String.class);
        assertEquals("HelloWorld", out, "Get a wrong output ");
    }

    @Test
    public void testEchoEndpointWithIgnoreResponseBody() throws Exception {
        String out = template.requestBody("http://localhost:" + port1 + "/echo?ignoreResponseBody=true", "HelloWorld",
                String.class);
        assertNull(out, "Get a wrong output ");
    }

    @Test
    public void testPostParameter() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost("http://localhost:" + port1 + "/parameter");
        post.addHeader("request", "PostParameter");
        post.addHeader("others", "bloggs");

        HttpResponse response = client.execute(post);
        String out = context.getTypeConverter().convertTo(String.class, response.getEntity().getContent());
        assertEquals("PostParameter", out, "Get a wrong output ");

        client.close();
    }

    @Test
    public void testPostXMLMessage() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost("http://localhost:" + port1 + "/postxml");
        post.setEntity(new StringEntity(POST_MESSAGE, ContentType.APPLICATION_XML));

        HttpResponse response = client.execute(post);
        String out = context.getTypeConverter().convertTo(String.class, response.getEntity().getContent());
        assertEquals("OK", out, "Get a wrong output ");

        client.close();
    }

    @Test
    public void testPostParameterInURI() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost post = new HttpPost("http://localhost:" + port1 + "/parameter?request=PostParameter&others=bloggs");
        post.setEntity(new StringEntity(POST_MESSAGE, ContentType.APPLICATION_XML));

        HttpResponse response = client.execute(post);
        String out = context.getTypeConverter().convertTo(String.class, response.getEntity().getContent());
        assertEquals("PostParameter", out, "Get a wrong output ");

        client.close();
    }

    @Test
    public void testPutParameterInURI() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPut put = new HttpPut("http://localhost:" + port1 + "/parameter?request=PutParameter&others=bloggs");
        put.setEntity(new StringEntity(POST_MESSAGE, ContentType.APPLICATION_XML));

        HttpResponse response = client.execute(put);
        String out = context.getTypeConverter().convertTo(String.class, response.getEntity().getContent());
        assertEquals("PutParameter", out, "Get a wrong output ");

        client.close();
    }

    @Test
    public void testDisableStreamCache() throws Exception {
        String response = template.requestBodyAndHeader("http://localhost:" + port3 + "/noStreamCache",
                new ByteArrayInputStream("This is a test".getBytes()), "Content-Type",
                "application/xml", String.class);

        assertEquals("OK", response, "Get a wrong output ");
    }

    @Test
    public void testRequestBufferSize() throws Exception {
        InputStream in = this.getClass().getResourceAsStream("/META-INF/LICENSE.txt");
        int fileSize = in.available();
        String response = template.requestBodyAndHeader("http://localhost:" + port4 + "/requestBufferSize", in,
                Exchange.CONTENT_TYPE, "application/txt", String.class);
        assertEquals(fileSize, response.length(), "Got a wrong response.");
    }

    @Test
    public void testResponseCode() throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpGet get = new HttpGet("http://localhost:" + port1 + "/responseCode");

        HttpResponse response = client.execute(get);
        // just make sure we get the right
        assertEquals(400, response.getStatusLine().getStatusCode(), "Get a wrong status code.");

        client.close();
    }

    protected void invokeHttpEndpoint() throws IOException {
        template.requestBodyAndHeader("http://localhost:" + port1 + "/test", expectedBody, "Content-Type", "application/xml");
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                port1 = getPort();
                port2 = getNextPort();
                port3 = getNextPort();
                port4 = getNextPort();

                // enable stream cache
                context.setStreamCaching(true);

                from("jetty:http://localhost:" + port1 + "/test").to("mock:a");

                Processor proc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        try {
                            HttpMessage message = (HttpMessage) exchange.getIn();
                            HttpSession session = message.getRequest().getSession();
                            assertNotNull(session, "we should get session here");
                        } catch (Exception e) {
                            exchange.getMessage().setBody(e);
                        }
                        exchange.getMessage().setBody("<b>Hello World</b>");
                    }
                };

                from("jetty:http://localhost:" + port1 + "/responseCode").setHeader(Exchange.HTTP_RESPONSE_CODE, simple("400"));

                Processor printProcessor = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Message out = exchange.getMessage();
                        out.copyFrom(exchange.getIn());
                        log.info("The body's object is " + exchange.getIn().getBody());
                        log.info("Process body = " + exchange.getIn().getBody(String.class));
                        InputStreamCache cache = out.getBody(InputStreamCache.class);
                        cache.reset();
                    }
                };
                from("jetty:http://localhost:" + port2 + "/hello?sessionSupport=true").process(proc);

                from("jetty:http://localhost:" + port1 + "/echo").process(printProcessor).process(printProcessor);

                Processor procParameters = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // As the request input stream is cached by
                        // DefaultHttpBinding,
                        // HttpServletRequest can't get the parameters of post
                        // message
                        String value = exchange.getIn().getHeader("request", String.class);
                        if (value != null) {
                            assertNotNull(value, "The value of the parameter should not be null");
                            exchange.getMessage().setBody(value);
                        } else {
                            exchange.getMessage().setBody("Can't get a right parameter");
                        }
                    }
                };

                from("jetty:http://localhost:" + port1 + "/parameter").process(procParameters);

                from("jetty:http://localhost:" + port1 + "/postxml").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String value = exchange.getIn().getBody(String.class);
                        assertEquals(POST_MESSAGE, value, "The response message is wrong");
                        exchange.getMessage().setBody("OK");
                    }
                });

                from("jetty:http://localhost:" + port3 + "/noStreamCache?disableStreamCache=true").noStreamCaching()
                        .process(new Processor() {
                            public void process(Exchange exchange) throws Exception {
                                InputStream is = (InputStream) exchange.getIn().getBody();
                                assertTrue(is instanceof org.eclipse.jetty.server.HttpInput, "It should be a raw inputstream");
                                String request = exchange.getIn().getBody(String.class);
                                assertEquals("This is a test", request, "Got a wrong request");
                                exchange.getMessage().setBody("OK");
                            }
                        });

                from("jetty:http://localhost:" + port4 + "/requestBufferSize").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String string = exchange.getIn().getBody(String.class);
                        exchange.getMessage().setBody(string);
                    }
                });
            }
        };
    }
}
