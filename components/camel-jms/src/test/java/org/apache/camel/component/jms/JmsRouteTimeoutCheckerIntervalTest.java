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
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.infra.activemq.services.ActiveMQService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Unit test for testing request timeout with a InOut exchange.
 */
@TestInstance(TestInstance.Lifecycle.PER_METHOD)
public class JmsRouteTimeoutCheckerIntervalTest extends AbstractJMSTest {

    @Test
    public void testTimeout() {
        try {
            // send a in-out with a timeout for 1 sec 
            template.requestBody("activemq:queue:JmsRouteTimeoutCheckerIntervalTest.slow?requestTimeout=1000", "Hello World");
            fail("Should have timed out with an exception");
        } catch (RuntimeCamelException e) {
            assertTrue(e.getCause() instanceof ExchangeTimedOutException, "Should have timed out with an exception");
        }
    }

    @Test
    public void testNoTimeout() {
        // START SNIPPET: e1
        // send a in-out with a timeout for 5 sec
        Object out = template.requestBody("activemq:queue:JmsRouteTimeoutCheckerIntervalTest.slow?requestTimeout=5000",
                "Hello World");
        // END SNIPPET: e1
        assertEquals("Bye World", out);
    }

    @Override
    protected String getComponentName() {
        return "activemq";
    }

    @Override
    protected JmsComponent setupComponent(CamelContext camelContext, ActiveMQService service, String componentName) {
        final JmsComponent component = super.setupComponent(camelContext, service, componentName);

        // check 4 times per second
        component.getConfiguration().setRequestTimeoutCheckerInterval(250);
        return component;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("activemq:queue:JmsRouteTimeoutCheckerIntervalTest.slow").delay(3000).transform(constant("Bye World"));
            }
        };
    }
}
