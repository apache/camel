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
package org.apache.camel.component.jms.tx;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWith;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.jms.AbstractSpringJMSTestSupport;
import org.apache.camel.component.mock.MockEndpoint;
import org.awaitility.Awaitility;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tags({ @Tag("not-parallel"), @Tag("spring"), @Tag("tx") })
public class JMSTransactionIsTransactedRedeliveredTest extends AbstractSpringJMSTestSupport {

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/component/jms/tx/JMSTransactionIsTransactedRedeliveredTest.xml");
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    protected MBeanServer getMBeanServer() {
        return context.getManagementStrategy().getManagementAgent().getMBeanServer();
    }

    @Test
    public void testTransactionSuccess() throws Exception {
        AdviceWith.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() {
                onException(AssertionError.class).to("log:error", "mock:error");
            }
        });
        context.start();

        // there should be no assertion errors
        MockEndpoint error = getMockEndpoint("mock:error");
        error.expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodiesReceived("Bye World");
        // success at 3rd attempt
        mock.message(0).header("count").isEqualTo(3);

        template.sendBody("activemq:queue:okay.JMSTransactionIsTransactedRedeliveredTest", "Hello World");

        mock.assertIsSatisfied();
        error.assertIsSatisfied();

        // check JMX stats
        // need a little sleep to ensure JMX is updated
        final Set<ObjectName> objectNames = Awaitility.await().atMost(1000, TimeUnit.MILLISECONDS)
                .until(() -> getMBeanServer()
                        .queryNames(new ObjectName("org.apache.camel:context=camel-*,type=routes,name=\"myRoute\""), null),
                        Matchers.hasSize(1));

        assertEquals(1, objectNames.size());
        ObjectName name = objectNames.iterator().next();

        Long total = (Long) getMBeanServer().getAttribute(name, "ExchangesTotal");
        assertEquals(3, total.intValue());

        Long completed = (Long) getMBeanServer().getAttribute(name, "ExchangesCompleted");
        assertEquals(1, completed.intValue());

        Long failed = (Long) getMBeanServer().getAttribute(name, "ExchangesFailed");
        assertEquals(2, failed.intValue());

        // Camel error handler redeliveries (we do not use that in this example)
        Long redeliveries = (Long) getMBeanServer().getAttribute(name, "Redeliveries");
        assertEquals(0, redeliveries.intValue());
        // Camel error handler redeliveries (we do not use that in this example)

        // there should be 2 external redeliveries
        Long externalRedeliveries = (Long) getMBeanServer().getAttribute(name, "ExternalRedeliveries");
        assertEquals(2, externalRedeliveries.intValue());
    }

    public static class MyProcessor implements Processor {
        private int count;

        @Override
        public void process(Exchange exchange) {
            ++count;

            // the first is not redelivered
            if (count == 1) {
                assertFalse(exchange.isExternalRedelivered(), "Should not be external redelivered");
            } else {
                assertTrue(exchange.isExternalRedelivered(), "Should be external redelivered");
            }

            if (count < 3) {
                throw new IllegalArgumentException("Forced exception");
            }
            exchange.getIn().setBody("Bye World");
            exchange.getIn().setHeader("count", count);
        }
    }

}
