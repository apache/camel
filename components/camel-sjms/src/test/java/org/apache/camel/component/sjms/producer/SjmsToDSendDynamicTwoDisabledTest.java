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
package org.apache.camel.component.sjms.producer;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.sjms.support.JmsTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SjmsToDSendDynamicTwoDisabledTest extends JmsTestSupport {

    @Test
    public void testToD() {
        template.sendBodyAndHeader("direct:start", "Hello bar", "where", "bar.SjmsToDSendDynamicTwoDisabledTest");
        template.sendBodyAndHeader("direct:start", "Hello beer", "where", "beer.SjmsToDSendDynamicTwoDisabledTest");
        template.sendBodyAndHeader("direct:start", "Hello gin", "where", "gin.SjmsToDSendDynamicTwoDisabledTest");

        template.sendBodyAndHeader("direct:start2", "Hello beer", "where2", "beer.SjmsToDSendDynamicTwoDisabledTest");
        template.sendBodyAndHeader("direct:start2", "Hello whiskey", "where2", "whiskey.SjmsToDSendDynamicTwoDisabledTest");

        // there should be 4 sjms endpoint
        long count = context.getEndpoints().stream().filter(e -> e.getEndpointUri().startsWith("sjms:")).count();
        assertEquals(4, count, "There should be 4 sjms endpoint");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // route message dynamic using toD but turn off send dynamic aware
                from("direct:start").toD().allowOptimisedComponents(false).uri("sjms:queue:${header.where}");
                from("direct:start2").toD().allowOptimisedComponents(false).uri("sjms:queue:${header.where2}");
            }
        };
    }

}
