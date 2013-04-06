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
package org.apache.camel.component.mina;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test to verify that MINA can be used with an InOnly MEP but still use sync to send and receive data
 * from a remote server.
 */
public class MinaInOnlyRouteTest extends BaseMinaTest {

    @Test
    public void testInOnlyUsingMina() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye Claus");
        mock.setResultWaitTime(5000);        

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("mina:tcp://localhost:{{port}}?sync=true").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = exchange.getIn().getBody(String.class);
                        exchange.getOut().setBody("Bye " + body);
                    }
                });

                from("timer://start?period=10000&delay=2000")
                        .setBody(constant("Claus"))                
                        .to("mina:tcp://localhost:{{port}}?sync=true&lazySessionCreation=true")
                        .to("mock:result");
            }
        };
    }
}
