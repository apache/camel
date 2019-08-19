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

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.reifier.RouteReifier;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Test;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Test that exchange.isExternalRedelivered() is kept around even when
 * Message implementation changes from JmsMessage to DefaultMessage, when routing
 * from JMS over Jetty.
 */
public class JMSTransactionIsTransactedRedeliveredTest extends CamelSpringTestSupport {

    private static int port = AvailablePortFinder.getNextAvailable();
    static {
        //set them as system properties so Spring can use the property placeholder
        //things to set them into the URL's in the spring contexts
        System.setProperty("Jetty.port", Integer.toString(port));
    }

    @Override
    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(
                "/org/apache/camel/itest/jms/JMSTransactionIsTransactedRedeliveredTest.xml");
    }

    @Override
    public boolean isUseAdviceWith() {
        return true;
    }

    @Test
    public void testTransactionSuccess() throws Exception {
        RouteReifier.adviceWith(context.getRouteDefinitions().get(0), context, new AdviceWithRouteBuilder() {
            @Override
            public void configure() throws Exception {
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

        MockEndpoint jetty = getMockEndpoint("mock:jetty");
        jetty.expectedMessageCount(1);

        template.sendBody("activemq:queue:okay", "Hello World");

        mock.assertIsSatisfied();
        jetty.assertIsSatisfied();
        error.assertIsSatisfied();
    }

    public static class MyBeforeProcessor implements Processor {
        private int count;

        @Override
        public void process(Exchange exchange) throws Exception {
            ++count;

            // the first is not redelivered
            if (count == 1) {
                assertFalse("Should not be external redelivered", exchange.isExternalRedelivered());
            } else {
                assertTrue("Should be external redelivered", exchange.isExternalRedelivered());
            }

            if (count < 3) {
                throw new IllegalArgumentException("Forced exception");
            }
            exchange.getIn().setHeader("count", count);
        }
    }

    public static class MyAfterProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            // origin message should be a external redelivered
            assertTrue("Should be external redelivered", exchange.isExternalRedelivered());
        }
    }

}
