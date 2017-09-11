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
package org.apache.camel.component.restlet;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.MediaType;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Status;

public class RestletRouteBuilderTest extends RestletTestSupport {
    private static final String ID = "89531";
    private static final String JSON = "{\"document type\": \"JSON\"}";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                
                // Restlet producer to use POST method. The RestletMethod=post will be stripped
                // before request is sent.
                from("direct:start").setHeader("id", constant(ID))
                    .to("restlet:http://localhost:" + portNum + "/orders?restletMethod=post&foo=bar");

                // Restlet consumer to handler POST method
                from("restlet:http://localhost:" + portNum + "/orders?restletMethod=post").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody(
                            "received [" + exchange.getIn().getBody(String.class)
                            + "] as an order id = "
                            + exchange.getIn().getHeader("id"));
                    }
                });

                // Restlet consumer to handler POST method
                from("restlet:http://localhost:" + portNum + "/ordersJSON?restletMethod=post").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        if (body.indexOf("{") == -1) {
                            throw new Exception("Inproperly formatted JSON:  " + body);
                        }
                        exchange.getOut().setBody(body);
                    }
                });

                // Restlet consumer default to handle GET method
                from("restlet:http://localhost:" + portNum + "/orders/{id}/{x}").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody(
                            "received GET request with id="
                            + exchange.getIn().getHeader("id")
                            + " and x="
                            + exchange.getIn().getHeader("x"));
                    }
                });

                // Restlet consumer to handler FORM POST method
                from("restlet:http://localhost:" + portNum + "/login?restletMethod=post").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody(
                            "received user: "
                            + exchange.getIn().getHeader("user")
                            + "password: "
                            + exchange.getIn().getHeader("passwd"));
                    }
                });
            }
        };
    }

    @Test
    public void testProducer() throws IOException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.CONTENT_TYPE, MediaType.APPLICATION_XML);
        
        String response = template.requestBodyAndHeaders("direct:start", "<order foo='1'/>", headers, String.class);
        assertEquals("received [<order foo='1'/>] as an order id = " + ID, response);
        
        headers.put("id", "89531");
        
        response = template.requestBodyAndHeaders(
            "restlet:http://localhost:" + portNum + "/orders?restletMethod=post&foo=bar", 
            "<order foo='1'/>", headers, String.class);
        assertEquals("received [<order foo='1'/>] as an order id = " + ID, response);
    }

    @Test
    public void testProducerJSON() throws IOException {
        String response = template.requestBodyAndHeader(
            "restlet:http://localhost:" + portNum + "/ordersJSON?restletMethod=post&foo=bar", 
            JSON,
            Exchange.CONTENT_TYPE,
            MediaType.APPLICATION_JSON,
            String.class);
           
        assertEquals(JSON, response);
    }

    @Test
    public void testProducerJSONFailure() throws IOException {
        String response = template.requestBodyAndHeader(
            "restlet:http://localhost:" + portNum + "/ordersJSON?restletMethod=post&foo=bar", 
            "{'JSON'}",
            Exchange.CONTENT_TYPE,
            MediaType.APPLICATION_JSON, String.class);
           
        assertEquals("{'JSON'}", response);
    }

    @Test
    public void testConsumer() throws IOException {
        Client client = new Client(Protocol.HTTP);
        Response response = client.handle(new Request(Method.GET, 
            "http://localhost:" + portNum + "/orders/99991/6"));
        assertEquals("received GET request with id=99991 and x=6",
            response.getEntity().getText());
    }

    @Test
    public void testUnhandledConsumer() throws IOException {
        Client client = new Client(Protocol.HTTP);
        Response response = client.handle(new Request(Method.POST, 
            "http://localhost:" + portNum + "/orders/99991/6"));
        // expect error status as no Restlet consumer to handle POST method
        assertEquals(Status.CLIENT_ERROR_METHOD_NOT_ALLOWED, response.getStatus());
        assertNotNull(response.getEntity().getText());
    }

    @Test
    public void testNotFound() throws IOException {
        Client client = new Client(Protocol.HTTP);
        Response response = client.handle(new Request(Method.POST,
            "http://localhost:" + portNum + "/unknown"));
        // expect error status as no Restlet consumer to handle POST method
        assertEquals(Status.CLIENT_ERROR_NOT_FOUND, response.getStatus());
        assertNotNull(response.getEntity().getText());
    }
    
    @Test
    public void testFormsProducer() throws IOException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.CONTENT_TYPE, MediaType.APPLICATION_WWW_FORM);
        
        String response = template.requestBodyAndHeaders("restlet:http://localhost:" + portNum + "/login?restletMethod=post", "user=jaymandawg&passwd=secret$%", headers, String.class);
        assertEquals("received user: jaymandawgpassword: secret$%", response);
    }
    
    @Test
    public void testFormsProducerMapBody() throws IOException {
        Map<String, Object> headers = new HashMap<String, Object>();
        headers.put(Exchange.CONTENT_TYPE, MediaType.APPLICATION_WWW_FORM);
        Map<String, String> body = new HashMap<>();
        body.put("user", "jaymandawg");
        body.put("passwd", "secret$%");
        String response = template.requestBodyAndHeaders("restlet:http://localhost:" + portNum + "/login?restletMethod=post", body, headers, String.class);
        assertEquals("received user: jaymandawgpassword: secret$%", response);
    }
}
