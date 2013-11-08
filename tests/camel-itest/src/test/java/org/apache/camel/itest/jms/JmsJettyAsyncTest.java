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

import java.util.concurrent.TimeUnit;
import javax.jms.ConnectionFactory;
import javax.naming.Context;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 *
 */
public class JmsJettyAsyncTest extends CamelTestSupport {

    private int size = 100;
    private int port;

    @Test
    public void testJmsJettyAsyncTest() throws Exception {
        getMockEndpoint("mock:result").expectedMessageCount(size);
        getMockEndpoint("mock:result").expectsNoDuplicates(body());

        for (int i = 0; i < size; i++) {
            template.sendBody("activemq:queue:inbox", "" + i);
        }

        assertMockEndpointsSatisfied(2, TimeUnit.MINUTES);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        port = AvailablePortFinder.getNextAvailable(8000);

        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // enable async consumer to process messages faster
                from("activemq:queue:inbox?asyncConsumer=false")
                    .to("jetty:http://0.0.0.0:" + port + "/myapp")
                    .to("log:result?groupSize=10", "mock:result");

                from("jetty:http://0.0.0.0:" + port + "/myapp")
                    .delay(100)
                    .transform(body().prepend("Bye "));
            }
        };
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();

        // add ActiveMQ with embedded broker
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        JmsComponent amq = jmsComponentAutoAcknowledge(connectionFactory);
        amq.setCamelContext(context);

        answer.bind("activemq", amq);
        return answer;
    }
}
