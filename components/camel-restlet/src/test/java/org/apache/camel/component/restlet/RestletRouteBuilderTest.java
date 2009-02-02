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

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.restlet.Client;
import org.restlet.data.Method;
import org.restlet.data.Protocol;
import org.restlet.data.Request;
import org.restlet.data.Response;
import org.restlet.data.Status;

public class RestletRouteBuilderTest extends ContextTestSupport {
    private static final String ID = "89531";

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                
                // Restlet producer to use POST method. The RestletMethod=post will be stripped
                // before request is sent.
                from("direct:start").setHeader("id", constant(ID))
                    .to("restlet:http://localhost:9080/orders?restletMethod=post&foo=bar");

                // Restlet consumer to handler POST method
                from("restlet:http://localhost:9080/orders?restletMethod=post").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody(
                                "received [" + exchange.getIn().getBody(String.class)
                                + "] as an order id = "
                                + exchange.getIn().getHeader("id"));
                    }
                });

                // Restlet consumer default to handle GET method
                from("restlet:http://localhost:9080/orders/{id}/{x}").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        exchange.getOut().setBody(
                                "received GET request with id="
                                + exchange.getIn().getHeader("id")
                                + " and x="
                                + exchange.getIn().getHeader("x"));
                    }
                });
            }
        };
    }

    
    public void testProducer() throws IOException {
        String response = (String)template.requestBody("direct:start", "<order foo='1'/>");
        assertEquals("received [<order foo='1'/>] as an order id = " + ID, response);
    }

    public void testConsumer() throws IOException {
        Client client = new Client(Protocol.HTTP);
        Response response = client.handle(new Request(Method.GET, 
                "http://localhost:9080/orders/99991/6"));
        assertEquals("received GET request with id=99991 and x=6",
                response.getEntity().getText());
    }
    
    public void testUnhandledConsumer() throws IOException {
        Client client = new Client(Protocol.HTTP);
        Response response = client.handle(new Request(Method.POST, 
                "http://localhost:9080/orders/99991/6"));
        // expect error status as no Restlet consumer to handle POST method
        assertEquals(Status.CLIENT_ERROR_NOT_FOUND, response.getStatus());
        assertNotNull(response.getEntity().getText());
    }

}
