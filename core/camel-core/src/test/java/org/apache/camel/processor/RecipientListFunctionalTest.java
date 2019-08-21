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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class RecipientListFunctionalTest extends ContextTestSupport {

    @Test
    public void testRecipientList() throws Exception {
        MockEndpoint x = getMockEndpoint("mock:x");
        x.expectedBodiesReceived("answer");
        x.expectedHeaderReceived("OnPrepare", true);

        MockEndpoint y = getMockEndpoint("mock:y");
        y.expectedBodiesReceived("answer");
        y.expectedHeaderReceived("OnPrepare", true);

        MockEndpoint z = getMockEndpoint("mock:z");
        z.expectedBodiesReceived("answer");
        z.expectedHeaderReceived("OnPrepare", true);

        template.sendBodyAndHeader("direct:a", "answer", "Endpoints", "mock:x,mock:y,mock:z");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").recipientList().message(m -> m.getHeader("Endpoints", String.class).split(",")).onPrepare().message(m -> m.setHeader("OnPrepare", true));
            }
        };
    }
}
