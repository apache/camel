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

package org.apache.camel.component.jms.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.AbstractPersistentJMSTest;
import org.junit.jupiter.api.Test;

public class JmsToDSendDynamicIT extends AbstractPersistentJMSTest {

    @Test
    public void testToD() {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "JmsToDSendDynamicIT.bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "JmsToDSendDynamicIT.beer");

        // there should only be one activemq endpoint
        long count = context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith("activemq:"))
                .count();
        assertEquals(1, count, "There should only be 1 activemq endpoint");

        // and the messages should be in the queues
        String out = consumer.receiveBody("activemq:queue:JmsToDSendDynamicIT.bar", 2000, String.class);
        assertEquals("Hello bar", out);
        out = consumer.receiveBody("activemq:queue:JmsToDSendDynamicIT.beer", 2000, String.class);
        assertEquals("Hello beer", out);
    }

    @Test
    public void testToDSlashed() {
        template.sendBodyAndHeader("direct:startSlashed", "Hello bar", "where", "JmsToDSendDynamicIT.bar");
        String out = consumer.receiveBody("activemq://JmsToDSendDynamicIT.bar", 2000, String.class);
        assertEquals("Hello bar", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // route message dynamic using toD
                from("direct:start").toD("activemq:queue:${header.where}");
                from("direct:startSlashed").toD("activemq://${header.where}");
            }
        };
    }
}
