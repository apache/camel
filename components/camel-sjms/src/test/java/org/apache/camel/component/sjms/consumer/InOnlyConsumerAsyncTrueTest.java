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
 *
 */
public class InOnlyConsumerAsyncTrueTest extends JmsTestSupport {

    private static final String MOCK_RESULT = "mock:result";
    
    @Test
    public void testInOnlyConsumerAsyncTrue() throws Exception {
        getMockEndpoint(MOCK_RESULT).expectedBodiesReceived("Hello World", "Hello Camel");

        template.sendBody("sjms:queue:in.only.consumer.async", "Hello Camel");
        template.sendBody("sjms:queue:in.only.consumer.async", "Hello World");
//        Thread.sleep(4000);

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("sjms:queue:in.only.consumer.async?synchronous=false").to("log:before")
                    .process(new Processor() {
                        public void process(Exchange exchange) throws Exception {
                            if (exchange.getIn().getBody(String.class).equals("Hello Camel")) {
                                Thread.sleep(2000);
                            }
                        }
                    }).to("log:after").to(MOCK_RESULT);
            }
        };
    }
}
