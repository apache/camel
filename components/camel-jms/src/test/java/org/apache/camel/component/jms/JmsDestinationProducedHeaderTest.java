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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsDestinationProducedHeaderTest extends AbstractPersistentJMSTest {

    @Test
    public void testToD() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(2);
        mock.message(0).header(JmsConstants.JMS_DESTINATION_NAME_PRODUCED).isEqualTo("JmsDestinationProducedHeaderTest.bar2");
        mock.message(1).header(JmsConstants.JMS_DESTINATION_NAME_PRODUCED).isEqualTo("JmsDestinationProducedHeaderTest.beer2");

        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "JmsDestinationProducedHeaderTest.bar2");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "JmsDestinationProducedHeaderTest.beer2");

        MockEndpoint.assertIsSatisfied(context);

        // there should only be two activemq endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("activemq:")).count();
        assertEquals(2, count, "There should only be 1 activemq endpoint");

        // and the messages should be in the queues
        String out = consumer.receiveBody("activemq:queue:JmsDestinationProducedHeaderTest.bar2", 2000, String.class);
        assertEquals("Hello bar", out);
        out = consumer.receiveBody("activemq:queue:JmsDestinationProducedHeaderTest.beer2", 2000, String.class);
        assertEquals("Hello beer", out);
    }

    @Test
    public void testToDInOut() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.message(0).header(JmsConstants.JMS_DESTINATION_NAME_PRODUCED).isEqualTo("JmsDestinationProducedHeaderTest.echo");

        String out = fluentTemplate.to("direct:start").withBody("Camel")
                .withHeader("where", "JmsDestinationProducedHeaderTest.echo")
                .request(String.class);
        assertEquals("CamelCamel", out);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // route message dynamic using toD
                from("direct:start")
                        .toD("activemq:queue:${header.where}")
                        .to("mock:result");

                from("activemq:queue:JmsDestinationProducedHeaderTest.echo")
                        .setBody(simple("${body}${body}"));
            }
        };
    }
}
