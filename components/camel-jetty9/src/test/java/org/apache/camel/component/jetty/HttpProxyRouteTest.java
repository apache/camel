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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.junit.Test;

public class HttpProxyRouteTest extends BaseJettyTest {

    private int size = 10;

    @Test
    public void testHttpProxy() throws Exception {
        log.info("Sending " + size + " messages to a http endpoint which is proxied/bridged");

        StopWatch watch = new StopWatch();
        for (int i = 0; i < size; i++) {
            String out = template.requestBody("http://localhost:{{port}}?foo=" + i, null, String.class);
            assertEquals("Bye " + i, out);
        }

        log.info("Time taken: " + TimeUtils.printDuration(watch.taken()));
    }
    
    @Test
    public void testHttpProxyWithDifferentPath() throws Exception {
        String out = template.requestBody("http://localhost:{{port}}/proxy", null, String.class);
        assertEquals("/otherEndpoint", out);
        
        out = template.requestBody("http://localhost:{{port}}/proxy/path", null, String.class);
        assertEquals("/otherEndpoint/path", out);
    }
    
    @Test
    public void testHttpProxyHostHeader() throws Exception {
        String out = template.requestBody("http://localhost:{{port}}/proxyServer", null, String.class);
        assertEquals("Get a wrong host header", "localhost:" + getPort2(), out);
    }
    
    @Test
    public void testHttpProxyFormHeader() throws Exception {
        String out = template.requestBodyAndHeader("http://localhost:{{port}}/form", "username=abc&pass=password", Exchange.CONTENT_TYPE, "application/x-www-form-urlencoded", String.class);
        assertEquals("Get a wrong response message", "username=abc&pass=password", out);
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("jetty://http://localhost:{{port}}")
                    .to("http://localhost:{{port}}/bye?throwExceptionOnFailure=false&bridgeEndpoint=true");
                
                from("jetty://http://localhost:{{port}}/proxy?matchOnUriPrefix=true")
                    .to("http://localhost:{{port}}/otherEndpoint?throwExceptionOnFailure=false&bridgeEndpoint=true");

                from("jetty://http://localhost:{{port}}/bye").transform(header("foo").prepend("Bye "));
                
                from("jetty://http://localhost:{{port}}/otherEndpoint?matchOnUriPrefix=true").transform(header(Exchange.HTTP_URI));
                
                from("jetty://http://localhost:{{port}}/proxyServer")
                    .to("http://localhost:{{port2}}/host?bridgeEndpoint=true");
                
                from("jetty://http://localhost:{{port2}}/host").transform(header("host"));
                
                // check the from request
                from("jetty://http://localhost:{{port}}/form?bridgeEndpoint=true")
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // just take out the message body and send it back
                            Message in = exchange.getIn();
                            String request = in.getBody(String.class);
                            exchange.getOut().setBody(request);
                        }
                        
                    });
            }
        };
    }    

}
