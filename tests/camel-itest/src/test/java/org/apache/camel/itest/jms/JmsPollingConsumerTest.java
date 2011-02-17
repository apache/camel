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
package org.apache.camel.itest.jms;

import org.apache.camel.ConsumerTemplate;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Handler;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

/**
 * @version 
 */
@ContextConfiguration
public class JmsPollingConsumerTest extends AbstractJUnit4SpringContextTests {

    @Produce(uri = "activemq:startConsumer")
    protected ProducerTemplate startConsumer;

    @Produce(uri = "direct:startConsumer")
    protected ProducerTemplate startDirectConsumer;

    @Produce(uri = "activemq:queue")
    protected ProducerTemplate queue;

    @EndpointInject(uri = "mock:result")
    protected MockEndpoint result;

    /**
     * Fails:
     * Consumer is expected to read two messages from activemq:queue and concatenate their bodies.
     * In this test, consumer bean is invoked from an activemq: route.
     */
    @Test
    @DirtiesContext
    @Ignore("CAMEL-2305")
    public void testConsumerFromJMSRoute() throws Exception {
        result.expectedBodiesReceived("foobar");

        queue.sendBody("foo");
        queue.sendBody("bar");

        startConsumer.sendBody("go");

        result.assertIsSatisfied();
    }

    /**
     * Succeeds:
     * Consumer is expected to read two messages from activemq:queue and concatenate their bodies.
     * In this test, consumer bean is invoked from a direct: route.
     */
    @Test
    @DirtiesContext
    public void testConsumerFromDirectRoute() throws Exception {
        result.expectedBodiesReceived("foobar");

        queue.sendBody("foo");
        queue.sendBody("bar");

        startDirectConsumer.sendBody("go");

        result.assertIsSatisfied();
    }

    public static class Consumer {

        @Autowired
        protected ConsumerTemplate consumer;

        @Handler
        public String consume() {
            StringBuilder result = new StringBuilder();

            Exchange exchange;
            while ((exchange = consumer.receive("activemq:queue", 2000)) != null) {
                result.append(exchange.getIn().getBody(String.class));
            }

            return result.toString();

        }
    }
}
