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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class RecipientListParallelTest extends ContextTestSupport {

    @Test
    public void testRecipientListParallel() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceivedInAnyOrder("c", "b", "a");

        template.sendBodyAndHeader("direct:start", "Hello World", "foo", "direct:a,direct:b,direct:c");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .recipientList(header("foo")).parallelProcessing();

                from("direct:a").delay(1000).transform(constant("a")).to("mock:result");
                from("direct:b").delay(500).transform(constant("b")).to("mock:result");
                from("direct:c").transform(constant("c")).to("mock:result");
            }
        };
    }
}
