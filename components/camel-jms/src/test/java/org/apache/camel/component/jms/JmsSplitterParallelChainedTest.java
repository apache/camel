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
package org.apache.camel.component.jms;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ExchangePattern;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Test that chained request/reply over JMS works in parallel mode with the splitter EIP.
 */
public class JmsSplitterParallelChainedTest extends AbstractJMSTest {

    @Order(2)
    @RegisterExtension
    public static CamelContextExtension camelContextExtension = new DefaultCamelContextExtension();
    protected CamelContext context;
    protected ProducerTemplate template;
    protected ConsumerTemplate consumer;

    protected String getUri() {
        return "activemq:queue:fooJmsSplitterParallelChainedTest";
    }

    protected String getUri2() {
        return "activemq:queue:barJmsSplitterParallelChainedTest";
    }

    @Test
    public void testSplitParallel() throws Exception {
        getMockEndpoint("mock:result").expectedBodiesReceived("A,B,C,D,E");
        getMockEndpoint("mock:reply").expectedBodiesReceivedInAnyOrder("Hi A", "Hi B", "Hi C", "Hi D", "Hi E");
        getMockEndpoint("mock:reply2").expectedBodiesReceivedInAnyOrder("Bye Hi A", "Bye Hi B", "Bye Hi C", "Bye Hi D",
                "Bye Hi E");
        getMockEndpoint("mock:split").expectedBodiesReceivedInAnyOrder("Bye Hi A", "Bye Hi B", "Bye Hi C", "Bye Hi D",
                "Bye Hi E");

        template.sendBody("direct:start", "A,B,C,D,E");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .split(body().tokenize(",")).parallelProcessing()
                        .to("log:before")
                        .to(ExchangePattern.InOut, getUri())
                        .to("log:after")
                        .to("mock:split")
                        .end()
                        .to("mock:result");

                from(getUri())
                        .transform(body().prepend("Hi "))
                        .to("mock:reply")
                        .to(ExchangePattern.InOut, getUri2());

                from(getUri2())
                        .transform(body().prepend("Bye "))
                        .to("mock:reply2");
            }
        };
    }

    @Override
    public CamelContextExtension getCamelContextExtension() {
        return camelContextExtension;
    }

    @BeforeEach
    void setUpRequirements() {
        context = camelContextExtension.getContext();
        template = camelContextExtension.getProducerTemplate();
        consumer = camelContextExtension.getConsumerTemplate();
    }
}
