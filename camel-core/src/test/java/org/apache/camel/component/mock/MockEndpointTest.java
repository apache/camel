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
package org.apache.camel.component.mock;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;

/**
 * @version $Revision: $
 */
public class MockEndpointTest extends ContextTestSupport {

    public void testAscendingMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectsAscending(header("counter").convertTo(Number.class));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }

    public void testAscendingMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectsAscending(header("counter").convertTo(Number.class));

        sendMessages(11, 12, 13, 15, 14);

        resultEndpoint.assertIsNotSatisfied();
    }

    public void testDescendingMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectsDescending(header("counter").convertTo(Number.class));

        sendMessages(15, 14, 13, 12, 11);

        resultEndpoint.assertIsSatisfied();
    }

    public void testDescendingMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectsDescending(header("counter").convertTo(Number.class));

        sendMessages(15, 14, 13, 11, 12);

        resultEndpoint.assertIsNotSatisfied();
    }

    public void testNoDuplicateMessagesPass() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectsNoDuplicates(header("counter"));

        sendMessages(11, 12, 13, 14, 15);

        resultEndpoint.assertIsSatisfied();
    }

    public void testDuplicateMessagesFail() throws Exception {
        MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
        resultEndpoint.expectsNoDuplicates(header("counter"));

        sendMessages(11, 12, 13, 14, 12);

        resultEndpoint.assertIsNotSatisfied();
    }

    protected void sendMessages(int... counters) {
        for (int counter : counters) {
            template.sendBodyAndHeader("direct:a", "<message>" + counter + "</message>",
                    "counter", counter);
        }
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").to("mock:result");
            }
        };
    }

}
