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
package org.apache.camel.component.mustache;

import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit test for {@link MustacheComponent} and {@link MustacheEndpoint}
 */
public class MustacheComponentTest extends CamelTestSupport {

    @EndpointInject("mock:endSimple")
    protected MockEndpoint endSimpleMock;

    @Produce("direct:startSimple")
    protected ProducerTemplate startSimpleProducerTemplate;

    /**
     * Main test
     */
    @Test
    public void testMustache() throws Exception {
        // Prepare
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.expectedBodiesReceived("\nMessage with body 'The Body' and some header 'Some Header'");
        // Act
        startSimpleProducerTemplate.sendBodyAndHeader("The Body", "someHeader", "Some Header");
        // Verify
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test using code Template header
     */
    @Test
    public void testMustacheWithTemplateHeader() throws Exception {
        // Prepare
        Exchange exchange = createExchangeWithBody("The Body");
        exchange.getIn().setHeader("someHeader", "Some Header");
        exchange.getIn().setHeader(MustacheConstants.MUSTACHE_TEMPLATE, "Body='{{body}}'|SomeHeader='{{headers.someHeader}}'");
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.expectedBodiesReceived("Body='The Body'|SomeHeader='Some Header'");
        // Act
        startSimpleProducerTemplate.send(exchange);
        // Verify
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Test using Resource URI header
     */
    @Test
    public void testMustacheWithResourceUriHeader() throws Exception {
        // Prepare
        Exchange exchange = createExchangeWithBody("The Body");
        exchange.getIn().setHeader("someHeader", "Some Header");
        exchange.getIn().setHeader(MustacheConstants.MUSTACHE_RESOURCE_URI, "/another.mustache");
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.message(0).body().contains("The Body");
        endSimpleMock.message(0).body().contains("Some Header");
        // Act
        startSimpleProducerTemplate.send(exchange);
        // Verify
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMustacheWithInheritance() throws Exception {
        // Prepare
        Exchange exchange = createExchangeWithBody("The Body");
        exchange.getIn().setHeader(MustacheConstants.MUSTACHE_RESOURCE_URI, "/child.mustache");
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.message(0).body().contains("Content 1: Child 1");
        endSimpleMock.message(0).body().contains("Middle");
        endSimpleMock.message(0).body().contains("Content 2: Child 2");
        // Act
        startSimpleProducerTemplate.send(exchange);
        // Verify
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testMustacheWithPartials() throws Exception {
        // Prepare
        Exchange exchange = createExchangeWithBody("The Body");
        exchange.getIn().setHeader(MustacheConstants.MUSTACHE_RESOURCE_URI, "/includer.mustache");
        endSimpleMock.expectedMessageCount(1);
        endSimpleMock.message(0).body().contains("Start");
        endSimpleMock.message(0).body().contains("Included");
        endSimpleMock.message(0).body().contains("End");
        // Act
        startSimpleProducerTemplate.send(exchange);
        // Verify
        MockEndpoint.assertIsSatisfied(context);
    }

    /**
     * Main test
     */
    @Test
    public void testMustachePerformance() throws Exception {
        int messageCount = 10000;
        endSimpleMock.expectedMessageCount(messageCount);
        StopWatch stopwatch = new StopWatch(true);
        for (int i = 0; i < messageCount; i++) {
            startSimpleProducerTemplate.sendBodyAndHeader("The Body", "someHeader", "Some Header");
        }
        MockEndpoint.assertIsSatisfied(context);
        LoggerFactory.getLogger(getClass())
                .info("Mustache performance: " + stopwatch.taken() + "ms for " + messageCount + " messages");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                MustacheComponent mc = context.getComponent("mustache", MustacheComponent.class);
                mc.setAllowTemplateFromHeader(true);

                from("direct:startSimple")
                        .to("mustache://simple.mustache")
                        .to("mock:endSimple");
            }
        };
    }
}
