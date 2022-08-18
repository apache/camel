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
package org.apache.camel.component.jms.async;

import java.util.concurrent.TimeUnit;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractJMSTest;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.StopWatch;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.apache.camel.test.infra.activemq.common.ConnectionFactoryHelper.createConnectionFactory;
import static org.apache.camel.test.junit5.TestSupport.body;

@Tags({ @Tag("not-parallel") })
public class AsyncJmsInOutTest extends AbstractJMSTest {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncJmsInOutTest.class);

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory
                = createConnectionFactory(service);
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Test
    public void testAsyncJmsInOut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(100);
        mock.expectsNoDuplicates(body());

        StopWatch watch = new StopWatch();

        for (int i = 0; i < 100; i++) {
            template.sendBody("seda:start", "" + i);
        }

        // just in case we run on slow boxes
        assertMockEndpointsSatisfied(20, TimeUnit.SECONDS);

        LOG.info("Took " + watch.taken() + " ms. to process 100 messages request/reply over JMS");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // in a fully sync mode it would take at least 5 + 5 sec to process the 100 messages
                // (there are delays in both routes)
                // however due async routing, we can leverage the fact to let threads non blocked
                // in the first route, and therefore can have the messages processed faster
                // because we can have messages wait concurrently in both routes
                // this means the async processing model is about 2x faster

                from("seda:start")
                        // we can only send at fastest the 100 msg in 5 sec due the delay
                        .delay(50)
                        .to(ExchangePattern.InOut, "activemq:queue:bar")
                        .to("mock:result");

                from("activemq:queue:bar")
                        .log("Using ${threadName} to process ${body}")
                        // we can only process at fastest the 100 msg in 5 sec due the delay
                        .delay(50)
                        .transform(body().prepend("Bye "));
            }
        };
    }
}
