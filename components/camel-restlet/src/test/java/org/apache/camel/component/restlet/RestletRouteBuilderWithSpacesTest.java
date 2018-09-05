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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.restlet.Client;
import org.restlet.Request;
import org.restlet.Response;
import org.restlet.data.Method;
import org.restlet.data.Protocol;

public class RestletRouteBuilderWithSpacesTest extends RestletTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                
                // Restlet consumer default to handle GET method
                from("restlet:http://localhost:" + portNum + "/orders with spaces in path/{id}/{x}").process(new Processor() {
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

    @Test
    public void testConsumerWithSpaces() throws IOException {
        Client client = new Client(Protocol.HTTP);
        Response response = client.handle(new Request(Method.GET, 
            "http://localhost:" + portNum + "/orders with spaces in path/99991/6"));
        assertEquals("received GET request with id=99991 and x=6",
            response.getEntity().getText());
    }
    
}
