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
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.TryType;

/**
 * No catch blocks but handle all should work
 *
 * @author <a href="mailto:nsandhu@raleys.com">nsandhu</a>
 */
public class ValidationFinallyBlockNoCatchTest extends ContextTestSupport {
    protected Processor validator = new MyValidator();
    protected MockEndpoint validEndpoint;
    protected MockEndpoint allEndpoint;

    public void testValidMessage() throws Exception {
        validEndpoint.expectedMessageCount(1);
        allEndpoint.expectedMessageCount(1);

        try {
            template.sendBodyAndHeader("direct:start", "<valid/>", "foo", "bar");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    public void testInvalidMessage() throws Exception {
        validEndpoint.expectedMessageCount(0);
        
        // allEndpoint receives 1 + 5 messages, ordinary (1 attempt) and redelivery (5 attempts) is involved
        allEndpoint.expectedMessageCount(1 + 5);

        try {
            template.sendBodyAndHeader("direct:start", "<invalid/>", "foo", "notMatchedHeaderValue");
        } catch (Exception e) {
            // expected
        }

        assertMockEndpointsSatisfied();
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        validEndpoint = resolveMandatoryEndpoint("mock:valid", MockEndpoint.class);
        allEndpoint = resolveMandatoryEndpoint("mock:all", MockEndpoint.class);
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                TryType tryType = from("direct:start").tryBlock().
                        process(validator).
                        to("mock:valid");
                tryType.finallyBlock().to("mock:all");
            }
        };
    }
}
