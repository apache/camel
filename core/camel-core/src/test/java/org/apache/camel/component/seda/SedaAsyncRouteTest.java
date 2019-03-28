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
package org.apache.camel.component.seda;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * Unit test based on user forum request.
 */
public class SedaAsyncRouteTest extends ContextTestSupport {

    @Test
    public void testSendAsync() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        // START SNIPPET: e2
        Object out = template.requestBody("direct:start", "Hello World");
        assertEquals("OK", out);
        // END SNIPPET: e2

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            // START SNIPPET: e1
            public void configure() throws Exception {
                from("direct:start")
                    // send it to the seda queue that is async
                    .to("seda:next")
                    // return a constant response
                    .transform(constant("OK"));

                from("seda:next").to("mock:result");
            }
            // END SNIPPET: e1
        };
    }
}
