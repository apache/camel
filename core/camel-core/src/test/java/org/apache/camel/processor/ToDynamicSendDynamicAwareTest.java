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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bar.BarComponent;
import org.apache.camel.component.bar.BarConstants;
import org.junit.Test;

public class ToDynamicSendDynamicAwareTest extends ContextTestSupport {

    @Test
    public void testToDynamic() throws Exception {
        getMockEndpoint("mock:bar").expectedBodiesReceived("Hello Camel ordered beer", "Hello World ordered wine");
        // the post-processor should remove the header
        getMockEndpoint("mock:bar").allMessages().header(BarConstants.DRINK).isNull();

        template.sendBodyAndHeader("direct:start", "Hello Camel", "drink", "beer");
        template.sendBodyAndHeader("direct:start", "Hello World", "drink", "wine");

        assertMockEndpointsSatisfied();

        // there should only be a bar:order endpoint
        boolean found = context.getEndpointMap().containsKey("bar://order");
        assertTrue("There should only be one bar endpoint", found);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.addComponent("bar", new BarComponent());

                from("direct:start").toD("bar:order?drink=${header.drink}").to("mock:bar");
            }
        };
    }
}
