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
import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

/**
 * @version $Revision$
 */
public class ChoiceTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;

    public void testSendToFirstWhen() throws Exception {
        String body = "<one/>";
        x.expectedBodiesReceived(body);
        // The SpringChoiceTest.java can't setup the header by Spring configure file
        // x.expectedHeaderReceived("name", "a");
        expectsMessageCount(0, y, z);

        sendMessage("bar", body);

        assertMockEndpointsSatisifed();
    }

    public void testSendToSecondWhen() throws Exception {
        String body = "<two/>";
        y.expectedBodiesReceived(body);
        expectsMessageCount(0, x, z);

        sendMessage("cheese", body);

        assertMockEndpointsSatisifed();
    }

    public void testSendToOtherwiseClause() throws Exception {
        String body = "<three/>";
        z.expectedBodiesReceived(body);
        expectsMessageCount(0, x, y);

        sendMessage("somethingUndefined", body);

        assertMockEndpointsSatisifed();
    }

    protected void sendMessage(final Object headerValue, final Object body) throws Exception {
        template.sendBodyAndHeader("direct:start", body, "foo", headerValue);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").choice()
                        .when(header("foo").isEqualTo("bar")).setHeader("name", constant("a")).to("mock:x")
                        .when(header("foo").isEqualTo("cheese")).to("mock:y")
                        .otherwise().to("mock:z");
            }
        };
    }

}
