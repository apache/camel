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
package org.apache.camel.component.jetty;

import javax.servlet.http.HttpServletRequest;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

/**
 * Unit test for wiki demonstration.
 */
public class JettyRouteTest extends BaseJettyTest {

    @Test
    public void testSendToJetty() throws Exception {
        Object response = template.requestBody("http://localhost:{{port}}/myapp/myservice", "bookid=123");
        // convert the response to a String
        String body = context.getTypeConverter().convertTo(String.class, response);
        assertEquals("<html><body>Book 123 is Camel in Action</body></html>", body);
    }
    
    @Test
    public void testHttpProxyHostHeader() throws Exception {
        String out = template.requestBody("http://localhost:{{port}}/proxyServer", null, String.class);
        assertEquals("Get a wrong host header", "localhost:" + getPort2(), out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("jetty:http://localhost:{{port}}/myapp/myservice").process(new MyBookService());
                // END SNIPPET: e1
                
                from("jetty://http://localhost:{{port}}/proxyServer")
                    .to("http://localhost:{{port2}}/host?bridgeEndpoint=true");
            
                from("jetty://http://localhost:{{port2}}/host").transform(header("host"));
            }
        };
    }

    // START SNIPPET: e2
    public class MyBookService implements Processor {
        public void process(Exchange exchange) throws Exception {
            // just get the body as a string
            String body = exchange.getIn().getBody(String.class);

            // we have access to the HttpServletRequest here and we can grab it if we need it
            HttpServletRequest req = exchange.getIn().getBody(HttpServletRequest.class);
            assertNotNull(req);

            // for unit testing
            assertEquals("bookid=123", body);

            // send a html response
            exchange.getOut().setBody("<html><body>Book 123 is Camel in Action</body></html>");
        }
    }
    // END SNIPPET: e2

}
