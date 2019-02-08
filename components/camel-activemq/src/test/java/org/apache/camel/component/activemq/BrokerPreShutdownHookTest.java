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
package org.apache.camel.component.activemq;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class BrokerPreShutdownHookTest {

    static class TestProcessor implements Processor {

        boolean messageReceived;

        @Override
        public void process(final Exchange exchange) throws Exception {
            messageReceived = true;
        }
    }

    @Test
    public void testShouldCleanlyShutdownCamelBeforeStoppingBroker() throws Exception {
        final BrokerService broker = new BrokerService();
        broker.setBrokerName("testBroker");
        broker.setUseJmx(true);
        broker.setPersistent(false);
        broker.addConnector("vm://testBroker");

        final DefaultCamelContext camel = new DefaultCamelContext();
        camel.setName("test-camel");

        final CamelShutdownHook hook = new CamelShutdownHook(broker);
        hook.setCamelContext(camel);

        broker.start();

        camel.addComponent("testq", ActiveMQComponent.activeMQComponent("vm://testBroker?create=false"));

        final TestProcessor processor = new TestProcessor();
        camel.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("testq:test.in").delay(200).process(processor);
            }
        });
        camel.start();

        final ProducerTemplate producer = camel.createProducerTemplate();
        producer.sendBody("testq:test.in", "Hi!");
        producer.stop();

        broker.stop();

        assertTrue("Message should be received", processor.messageReceived);
        assertTrue("Camel context should be stopped", camel.isStopped());
        assertTrue("Broker should be stopped", broker.isStopped());

    }
}
