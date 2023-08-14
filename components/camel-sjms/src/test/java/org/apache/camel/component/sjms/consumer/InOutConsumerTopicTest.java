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
package org.apache.camel.component.sjms.consumer;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

public class InOutConsumerTopicTest extends JmsTestSupport {

    @Override
    protected boolean useJmx() {
        return true;
    }

    @Test
    public void testSynchronous() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("Hello Camel", "Hello World");
        template.sendBody("sjms:topic:start.topic.InOutConsumerTopicTest", "Hello Camel");
        template.sendBody("sjms:topic:start.topic.InOutConsumerTopicTest", "Hello World");
        Thread.sleep(3000);
        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("sjms:topic:start.topic.InOutConsumerTopicTest").to("log:request")
                        .to("sjms:topic:in.out.topic.InOutConsumerTopicTest?exchangePattern=InOut&replyTo=in.out.topic.response")
                        .to("log:response").to("mock:result");

                from("sjms:topic:in.out.topic.InOutConsumerTopicTest?exchangePattern=InOut").process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        String body = (String) exchange.getIn().getBody();
                        if (body.contains("Camel")) {
                            Thread.sleep(2000);
                        }
                    }
                });
            }
        };
    }

}
