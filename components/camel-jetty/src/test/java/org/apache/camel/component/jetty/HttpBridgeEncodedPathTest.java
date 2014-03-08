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

import java.io.ByteArrayInputStream;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class HttpBridgeEncodedPathTest extends BaseJettyTest {

    private int port1;
    private int port2;

    @Test
    public void testHttpClient() throws Exception {
        String response = template.requestBodyAndHeader("http://localhost:" + port2 + "/test/hello?param1=%2B447777111222",
                new ByteArrayInputStream("This is a test".getBytes()), "Content-Type", "text/plain", String.class);
        assertEquals("Get a wrong response", "param1=+447777111222", response);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                port1 = getPort();
                port2 = getNextPort();

                errorHandler(noErrorHandler());

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // %2B becomes decoded to a space
                        assertEquals(" 447777111222", exchange.getIn().getHeader("param1"));
                        // and in the http query %20 becomes a + sign
                        assertEquals("param1=+447777111222", exchange.getIn().getHeader(Exchange.HTTP_QUERY));

                        // send back the query
                        exchange.getOut().setBody(exchange.getIn().getHeader(Exchange.HTTP_QUERY));
                    }
                };
                from("jetty:http://localhost:" + port2 + "/test/hello")
                    .to("http://localhost:" + port1 + "?throwExceptionOnFailure=false&bridgeEndpoint=true");
                
                from("jetty://http://localhost:" + port1 + "?matchOnUriPrefix=true").process(serviceProc);
            }
        };
    }    

}
