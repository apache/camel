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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

public class JmsHeaderFilteringTest extends CamelTestSupport {

    private static final String IN_FILTER_PATTERN = "(org_apache_camel)[_|a-z|A-Z|0-9]*(test)[_|a-z|A-Z|0-9]*";

    private final String componentName = "jms";
    private final String testQueueEndpointA = componentName + ":queue:test.a";
    private final String testQueueEndpointB = componentName + ":queue:test.b";
    private final String assertionReceiver = "mock:errors";
    private CountDownLatch latch = new CountDownLatch(2);

    @Test
    public void testHeaderFilters() throws Exception {
        MockEndpoint errors = this.resolveMandatoryEndpoint(assertionReceiver, MockEndpoint.class);
        errors.expectedMessageCount(0);

        template.send(testQueueEndpointA, ExchangePattern.InOnly, exchange -> {
            exchange.getIn().setHeader("org.apache.camel.jms", 10000);
            exchange.getIn().setHeader("org.apache.camel.test.jms", 20000);
            exchange.getIn().setHeader("testheader", 1020);
            exchange.getIn().setHeader("anotherheader", 1030);
            exchange.getIn().setHeader("JMSXAppID", "myApp");
        });

        // make sure that the latch reached zero and that timeout did not elapse
        assertTrue(latch.await(2, TimeUnit.SECONDS));

        errors.assertIsSatisfied();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        JmsComponent component = camelContext.getComponent(componentName, JmsComponent.class);

        JmsHeaderFilterStrategy filter = new JmsHeaderFilterStrategy();
        filter.getInFilter().add("testheader");
        filter.getOutFilter().add("anotherheader");
        // add a regular expression pattern filter, notice that dots are encoded to '_DOT_' in jms headers
        filter.setInFilterPattern(IN_FILTER_PATTERN);

        component.setHeaderFilterStrategy(filter);

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {

                onException(AssertionError.class).to(assertionReceiver);

                from(testQueueEndpointA).process(new OutHeaderChecker()).to(testQueueEndpointB);
                from(testQueueEndpointB).process(new InHeaderChecker());
            }
        };
    }

    class OutHeaderChecker implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {
            JmsMessage message = exchange.getIn(JmsMessage.class);

            // testheader not filtered out until it is copied back to camel
            assertEquals(1020, message.getJmsMessage().getObjectProperty("testheader"));

            // anotherheader has been filtered out
            assertNull(message.getJmsMessage().getObjectProperty("anotherheader"));

            // notice dots are replaced by '_DOT_' when it is copied to the jms message properties
            assertEquals(10000, message.getJmsMessage().getObjectProperty("org_DOT_apache_DOT_camel_DOT_jms"));

            // like testheader, org.apache.camel.test.jms will be filtered by the "in" filter
            assertEquals(20000, message.getJmsMessage().getObjectProperty("org_DOT_apache_DOT_camel_DOT_test_DOT_jms"));

            // should be filtered by default
            assertNull(message.getJmsMessage().getStringProperty("JMSXAppID"));

            latch.countDown();
        }

    }

    class InHeaderChecker implements Processor {

        @Override
        public void process(Exchange exchange) throws Exception {

            // filtered out by "in" filter
            assertNull(exchange.getIn().getHeader("testheader"));

            // it has been filtered out by "out" filter
            assertNull(exchange.getIn().getHeader("anotherheader"));

            // it should not been filtered out
            assertEquals(10000, exchange.getIn().getHeader("org.apache.camel.jms"));

            // filtered out by "in" filter
            assertNull(exchange.getIn().getHeader("org_DOT_apache_DOT_camel_DOT_test_DOT_jms"));

            // should be filtered by default
            assertNull(exchange.getIn().getHeader("JMSXAppID"));

            latch.countDown();
        }

    }

}
