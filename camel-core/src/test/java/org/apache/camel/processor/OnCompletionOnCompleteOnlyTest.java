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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

/**
 * @version 
 */
public class OnCompletionOnCompleteOnlyTest extends OnCompletionTest {

    @Test
    public void testSynchronizeFailure() throws Exception {
        // do not expect a message since we only do onCompleteOnly
        getMockEndpoint("mock:sync").expectedMessageCount(0);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(0);

        try {
            template.sendBody("direct:start", "Kabom");
            fail("Should throw exception");
        } catch (CamelExecutionException e) {
            assertEquals("Kabom", e.getCause().getMessage());
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e1
                from("direct:start")
                    // here we qualify onCompletion to only invoke when the exchange completed with success
                    // if the exchange failed this onCompletion route will NOT be routed then
                    .onCompletion().onCompleteOnly()
                        .to("log:sync")
                        .to("mock:sync")
                    // must use end to denote the end of the onCompletion route
                    .end()
                    // here the original route contiues
                    .process(new MyProcessor())
                    .to("mock:result");
                // END SNIPPET: e1
            }
        };
    }

}