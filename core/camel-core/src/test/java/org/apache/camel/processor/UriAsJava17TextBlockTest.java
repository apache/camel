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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UriAsJava17TextBlockTest extends ContextTestSupport {

    @Test
    public void testUriTestBlock() throws Exception {
        assertEquals(1, context.getRoutesSize());

        MockEndpoint mock = getMockEndpoint("mock:result?retainFirst=123&failFast=false&resultWaitTime=5000");
        mock.expectedBodiesReceived("Hello World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(
                        """
                                   direct:start?
                                   block=false&
                                   timeout=1234
                                """)
                        .to("log:foo")
                        .to("log:bar")
                        .to("""
                                mock:result
                                ?retainFirst=123
                                &failFast=false
                                &resultWaitTime=5000""");
            }
        };
    }
}
