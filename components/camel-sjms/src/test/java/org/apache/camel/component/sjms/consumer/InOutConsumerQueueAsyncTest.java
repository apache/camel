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
package org.apache.camel.component.sjms.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;

import org.junit.Test;

/**
 * @version 
 */
public class InOutConsumerQueueAsyncTest extends JmsTestSupport {

    @Test
    public void testAynchronous() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World", "Hello Camel");

        template.sendBody("sjms:start", "Hello Camel");
        template.sendBody("sjms:start", "Hello World");
        Thread.sleep(4000);
        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("sjms:queue:start?synchronous=false")
                    .to("sjms:queue:in.out.queue?exchangePattern=InOut&synchronous=false&namedReplyTo=in.out.queue.response")
                    .to("mock:result");

                from("sjms:queue:in.out.queue?exchangePattern=InOut&synchronous=false").to("log:before")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            String body = (String)exchange.getIn().getBody();
                            if (body.contains("Camel")) {
                                Thread.sleep(2000);
                            }
                        }
                    });
            }
        };
    }

}
