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
package org.apache.camel.component.jms.issues;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.junit.jupiter.api.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;

public class JmsInOutPersistentReplyQueueTest extends AbstractJMSTest {

    @Test
    public void testInOutPersistentReplyQueue() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceivedInAnyOrder("Bye A", "Bye B", "Bye C", "Bye D");

        template.sendBody("seda:start", "A");
        template.sendBody("seda:start", "B");
        template.sendBody("seda:start", "C");
        template.sendBody("seda:start", "D");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));
        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("seda:start")
                        .log("Sending ${body}")
                        .to(ExchangePattern.InOut, "activemq:queue:JmsInOutPersistentReplyQueueTest?replyTo=myReplies")
                        // process the remainder of the route concurrently
                        .threads(5)
                        .log("Reply ${body}")
                        .delay(2000)
                        .to("mock:result");

                from("activemq:queue:JmsInOutPersistentReplyQueueTest")
                        .to("mock:foo")
                        .transform(body().prepend("Bye "))
                        .log("Sending back reply ${body}");
            }
        };
    }

}
