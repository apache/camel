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
package org.apache.camel.component.ironmq;

import java.io.IOException;

import io.iron.ironmq.EmptyQueueException;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class FromQueueToQueueTest extends CamelTestSupport {

    @EndpointInject(uri = "mock:result")
    private MockEndpoint result;

    @EndpointInject(uri = "ironmq:testqueue?client=#ironMock")
    private IronMQEndpoint queue1;

    @EndpointInject(uri = "ironmq:testqueue2?client=#ironMock")
    private IronMQEndpoint queue2;

    @Test
    public void shouldDeleteMessageFromQueue1() throws Exception {

        result.setExpectedMessageCount(1);

        template.send("direct:start", ExchangePattern.InOnly, new Processor() {
            @Override
            public void process(Exchange exchange) throws Exception {
                exchange.getIn().setBody("This is my message text.");
            }
        });

        assertMockEndpointsSatisfied();

        try {
            queue1.getClient().queue("testqueue").reserve();
            fail("Message was in the first queue!");
        } catch (IOException e) {
            if (!(e instanceof EmptyQueueException)) {
                // Unexpected exception.
                throw e;
            }
        }

        try {
            queue2.getClient().queue("testqueue1").reserve();
            fail("Message remained in second queue!");
        } catch (IOException e) {
            if (!(e instanceof EmptyQueueException)) {
                // Unexpected exception.
                throw e;
            }
        }
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry registry = super.createRegistry();
        registry.bind("ironMock", new IronMQClientMock("dummy", "dummy"));
        return registry;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to("ironmq:testqueue?client=#ironMock");
                from("ironmq:testqueue?client=#ironMock").to("ironmq:testqueue2?client=#ironMock");
                from("ironmq:testqueue2?client=#ironMock").to("mock:result");
            }
        };
    }

}
