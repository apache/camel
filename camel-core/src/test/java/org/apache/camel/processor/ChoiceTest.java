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

import static org.apache.camel.component.mock.MockEndpoint.assertIsSatisfied;
import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

/**
 * @version $Revision: 1.1 $
 */
public class ChoiceTest extends ContextTestSupport {
    protected Endpoint<Exchange> startEndpoint;
    protected MockEndpoint x, y, z;

    public void testSendToFirstWhen() throws Exception {
        x.expectedBodiesReceived("one");
        expectsMessageCount(0, y, z);

        sendMessage("bar", "one");

        assertIsSatisfied(x, y, z);
    }

    public void testSendToSecondWhen() throws Exception {
        y.expectedBodiesReceived("two");
        expectsMessageCount(0, x, z);

        sendMessage("cheese", "two");

        assertIsSatisfied(x, y, z);
    }

    public void testSendToOtherwiseClause() throws Exception {
        z.expectedBodiesReceived("three");
        expectsMessageCount(0, x, y);

        sendMessage("somethingUndefined", "three");

        assertIsSatisfied(x, y, z);
    }

    protected void sendMessage(final Object headerValue, final Object body) throws Exception {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("foo", headerValue);
            }
        });
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:a");

        x = (MockEndpoint) resolveMandatoryEndpoint("mock:x");
        y = (MockEndpoint) resolveMandatoryEndpoint("mock:y");
        z = (MockEndpoint) resolveMandatoryEndpoint("mock:z");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a").choice()
                        .when(header("foo").isEqualTo("bar")).to("mock:x")
                        .when(header("foo").isEqualTo("cheese")).to("mock:y")
                        .otherwise().to("mock:z");
            }
        };
    }

}
