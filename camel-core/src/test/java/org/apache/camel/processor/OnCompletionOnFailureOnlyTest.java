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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class OnCompletionOnFailureOnlyTest extends OnCompletionTest {

    @Test
    public void testSynchronizeComplete() throws Exception {
        // do not expect a message since we only do onFailureOnly
        getMockEndpoint("mock:sync").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");

        template.sendBody("direct:start", "Hello World");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // here we qualify onCompletion to only invoke when the exchange failed (exception or FAULT body)
                    .onCompletion().onFailureOnly()
                        .to("log:sync")
                        .to("mock:sync")
                    // must use end to denote the end of the onCompletion route
                    .end()
                    // here the original route continues
                    .process(new MyProcessor())
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}