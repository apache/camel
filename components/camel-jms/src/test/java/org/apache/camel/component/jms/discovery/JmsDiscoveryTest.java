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
package org.apache.camel.component.jms.discovery;

import java.util.HashMap;
import java.util.Map;
import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.camel.CamelContext;
import org.apache.camel.ShutdownRoute;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.CamelJmsTestHelper;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsDiscoveryTest extends CamelTestSupport {
    protected MyRegistry registry = new MyRegistry();

    @Test
    public void testDiscovery() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.setResultWaitTime(5000);
        // force shutdown after 5 seconds as otherwise the bean will keep generating a new input
        context.getShutdownStrategy().setTimeout(5);

        assertMockEndpointsSatisfied();

        // sleep a little
        Thread.sleep(1000);

        Map<String, Map<?, ?>> map = new HashMap<String, Map<?, ?>>(registry.getServices());
        assertTrue("There should be 1 or more, was: " + map.size(), map.size() >= 1);
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent("activemq", jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        context.bind("service1", new MyService("service1"));
        context.bind("registry", registry);
        return context;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                // lets setup the heartbeats
                from("timer:heartbeats?delay=100")
                    .to("bean:service1?method=status")
                    .to("activemq:topic:registry.heartbeats");

                // defer shutting this route down as the first route depends upon it to
                // be running so it can complete its current exchanges
                from("activemq:topic:registry.heartbeats")
                    .shutdownRoute(ShutdownRoute.Defer)
                    .to("bean:registry?method=onEvent", "mock:result");
            }
        };
    }
}
