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

import jakarta.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit test for wiki demonstration.
 */
public class JettyRouteTest extends BaseJettyTest {

    @Test
    public void testSendToJetty() {
        Object response = template.requestBody("http://localhost:{{port}}/myapp/myservice", "bookid=123");
        // convert the response to a String
        String body = context.getTypeConverter().convertTo(String.class, response);
        assertEquals("<html><body>Book 123 is Camel in Action</body></html>", body);
    }

    @Test
    public void testHttpProxyHostHeader() {
        String out = template.requestBody("http://localhost:{{port}}/proxyServer", null, String.class);
        assertEquals("localhost:" + getPort2(), out, "Get a wrong host header");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // START SNIPPET: e1
                from("jetty:http://localhost:{{port}}/myapp/myservice").process(new MyBookService());
                // END SNIPPET: e1

                from("jetty://http://localhost:{{port}}/proxyServer").to("http://localhost:{{port2}}/host?bridgeEndpoint=true");

                from("jetty://http://localhost:{{port2}}/host").transform(header("host"));
            }
        };
    }

    // START SNIPPET: e2
    public static class MyBookService implements Processor {
        @Override
        public void process(Exchange exchange) {
            // just get the body as a string
            String body = exchange.getIn().getBody(String.class);

            // we have access to the HttpServletRequest here and we can grab it
            // if we need it
            HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
            assertNotNull(req);

            // for unit testing
            assertEquals("bookid=123", body);

            // send a html response
            exchange.getMessage().setBody("<html><body>Book 123 is Camel in Action</body></html>");
        }
    }
    // END SNIPPET: e2

}
