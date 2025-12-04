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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;

/**
 * This test computes the number of components, so it could be affected by other tests. Therefore, it's run in
 * isolation.
 */
@Tags({@Tag("not-parallel"), @Tag("spring")})
public class JmsToDSendDynamicTwoDisabledIT extends AbstractPersistentJMSTest {

    @Test
    public void testToD() {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "JmsToDSendDynamicIT.bar");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "JmsToDSendDynamicIT.beer");
        template.sendBodyAndHeader("direct:start", "Hello gin", "where", "JmsToDSendDynamicIT.gin");

        template.sendBodyAndHeader("direct:start2", "Hello beer", "where2", "JmsToDSendDynamicIT.beer");
        template.sendBodyAndHeader("direct:start2", "Hello whiskey", "where2", "JmsToDSendDynamicIT.whiskey");

        // there should be 4 activemq endpoint
        long count = context.getEndpoints().stream()
                .filter(e -> e.getEndpointUri().startsWith("activemq:"))
                .count();
        assertEquals(4, count, "There should be 4 activemq endpoint");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // route message dynamic using toD but turn off send dynamic aware
                from("direct:start").toD().allowOptimisedComponents(false).uri("activemq:queue:${header.where}");
                from("direct:start2").toD().allowOptimisedComponents(false).uri("activemq:queue:${header.where2}");
            }
        };
    }
}
