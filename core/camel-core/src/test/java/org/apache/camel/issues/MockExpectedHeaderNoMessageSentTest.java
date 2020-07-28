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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class MockExpectedHeaderNoMessageSentTest extends ContextTestSupport {

    @Test
    public void testHeaderExpectedNoMessageSent() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.setResultWaitTime(100); // run test quick

        mock.expectedHeaderReceived("foo", "bar");

        try {
            mock.assertIsSatisfied();
            fail("Should fail");
        } catch (AssertionError e) {
            assertEquals("mock://result Received message count 0, expected at least 1", e.getMessage());
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:foo")
                        .routeId("myRoute")
                        .to("mock:result");
            }
        };
    }
}
