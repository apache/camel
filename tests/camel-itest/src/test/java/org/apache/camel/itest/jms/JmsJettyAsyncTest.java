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
import javax.naming.Context;

import org.apache.activemq.camel.component.ActiveMQComponent;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

/**
 *
 */
public class JmsJettyAsyncTest extends CamelTestSupport {

    // TODO: When async jms consumer is implemented we can bump this value to 1000
    private int size = 10;

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
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:inbox?synchronous=false")
                    .to("jetty:http://0.0.0.0:9432/myapp")
                    .to("log:result?groupSize=10", "mock:result");

                from("jetty:http://0.0.0.0:9432/myapp")
                    .delay(100)
                    .transform(body().prepend("Bye "));
            }
        };
    }

    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();

        // add ActiveMQ with embedded broker
        ActiveMQComponent amq = ActiveMQComponent.activeMQComponent("vm://localhost?broker.persistent=false");
        amq.setCamelContext(context);
        answer.bind("activemq", amq);
        return answer;
    }
}
