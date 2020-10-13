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
package org.apache.camel.itest.jms;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.utils.extensions.JmsServiceExtension;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class JmsIntegrationTest extends CamelTestSupport {
    @RegisterExtension
    public static JmsServiceExtension jmsServiceExtension = JmsServiceExtension.createExtension();

    private static final Logger LOG = LoggerFactory.getLogger(JmsIntegrationTest.class);

    protected CountDownLatch receivedCountDown = new CountDownLatch(1);
    protected MyBean myBean = new MyBean();

    @Test
    void testOneWayInJmsOutPojo() throws Exception {
        // Send a message to the JMS endpoint
        template.sendBodyAndHeader("jms:test", "Hello", "cheese", 123);

        // The Activated endpoint should send it to the pojo due to the configured route.
        assertTrue(receivedCountDown.await(5, TimeUnit.SECONDS), "The message ware received by the Pojo");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("jms:test").to("bean:myBean");
            }
        };
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        // add ActiveMQ with embedded broker
        JmsComponent amq = jmsServiceExtension.getComponent();

        amq.setCamelContext(context);

        registry.bind("myBean", myBean);
        registry.bind("jms", amq);
    }

    protected class MyBean {
        public void onMessage(String body) {
            LOG.info("Received: " + body);
            receivedCountDown.countDown();
        }
    }

}
