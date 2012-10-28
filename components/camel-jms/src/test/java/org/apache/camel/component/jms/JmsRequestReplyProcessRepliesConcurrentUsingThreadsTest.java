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
package org.apache.camel.component.jms;

import javax.jms.ConnectionFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;

/**
 * @version 
 */
public class JmsRequestReplyProcessRepliesConcurrentUsingThreadsTest extends CamelTestSupport {

    protected String componentName = "activemq";

    @Test
    public void testRequestReplyWithConcurrent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("Bye A", "Bye B", "Bye C", "Bye D", "Bye E");

        log.info("Sending messages ...");
        template.sendBody("seda:start", "A");
        template.sendBody("seda:start", "B");
        template.sendBody("seda:start", "C");
        template.sendBody("seda:start", "D");
        template.sendBody("seda:start", "E");
        log.info("... done sending messages");

        assertMockEndpointsSatisfied();
    }

    protected CamelContext createCamelContext() throws Exception {
        CamelContext camelContext = super.createCamelContext();

        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();
        camelContext.addComponent(componentName, jmsComponentAutoAcknowledge(connectionFactory));

        return camelContext;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("activemq:queue:foo")
                    .log("request - ${body}")
                    .transform(body().prepend("Bye "));

                from("seda:start")
                    .setExchangePattern(ExchangePattern.InOut)
                    .to("activemq:queue:foo")
                    .log("reply   - ${body}")
                    .threads(5)
                    .log("delay   - ${body}")
                    .delay(2000)
                    .log("done    - ${body}")
                    .to("mock:result");
            }
        };
    }
}