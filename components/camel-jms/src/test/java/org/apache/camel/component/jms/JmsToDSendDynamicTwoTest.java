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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JmsToDSendDynamicTwoTest extends AbstractPersistentJMSTest {

    @Test
    public void testToD() {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "JmsToDSendDynamicTwoTest.bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "JmsToDSendDynamicTwoTest.beer");
        template.sendBodyAndHeader("direct:start", "Hello gin", "where", "JmsToDSendDynamicTwoTest.gin");

        template.sendBodyAndHeader("direct:start2", "Hello beer", "where2", "JmsToDSendDynamicTwoTest.beer");
        template.sendBodyAndHeader("direct:start2", "Hello whiskey", "where2", "whiskey");

        // there should be 2 activemq endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("activemq:")).count();
        assertEquals(2, count, "There should only be 2 activemq endpoint");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // route message dynamic using toD
                from("direct:start").toD("activemq:queue:${header.where}");

                from("direct:start2").toD("activemq:queue:${header.where2}");
            }
        };
    }
}
