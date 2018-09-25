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
public class InOutConsumerTopicTest extends JmsTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testSynchronous() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel", "Hello World");
        template.sendBody("sjms:topic:start", "Hello Camel");
        template.sendBody("sjms:topic:start", "Hello World");
        Thread.sleep(3000);
        assertMockEndpointsSatisfied();
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("sjms:topic:start").to("log:request")
                    .to("sjms:topic:in.out.topic?exchangePattern=InOut&namedReplyTo=in.out.topic.response")
                    .to("log:response").to("mock:result");

                from("sjms:topic:in.out.topic?exchangePattern=InOut").process(new Processor() {
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
