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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;

public class HttpBridgeAsyncRouteTest extends HttpBridgeRouteTest {

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                port1 = getPort();
                port2 = getNextPort();

                errorHandler(noErrorHandler());

                Processor serviceProc = new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        // get the request URL and copy it to the request body
                        String uri = exchange.getIn().getHeader(Exchange.HTTP_URI, String.class);
                        exchange.getOut().setBody(uri);
                    }
                };
                from("jetty:http://localhost:" + port2 + "/test/hello?async=true&useContinuation=false")
                    .to("http://localhost:" + port1 + "?throwExceptionOnFailure=false&bridgeEndpoint=true");
                
                from("jetty://http://localhost:" + port1 + "?matchOnUriPrefix=true&async=true&useContinuation=false").process(serviceProc);
            }
        };
    }    

}
