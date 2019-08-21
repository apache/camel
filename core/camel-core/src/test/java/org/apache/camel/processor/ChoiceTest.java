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
import org.junit.Before;
import org.junit.Test;

import static org.apache.camel.component.mock.MockEndpoint.expectsMessageCount;

public class ChoiceTest extends ContextTestSupport {
    protected MockEndpoint x;
    protected MockEndpoint y;
    protected MockEndpoint z;
    protected MockEndpoint end;

    @Test
    public void testSendToFirstWhen() throws Exception {
        String body = "<one/>";
        x.expectedBodiesReceived(body);
        end.expectedBodiesReceived(body);
        // The SpringChoiceTest.java can't setup the header by Spring configure
        // file
        // x.expectedHeaderReceived("name", "a");
        expectsMessageCount(0, y, z);

        sendMessage("bar", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendToSecondWhen() throws Exception {
        String body = "<two/>";
        y.expectedBodiesReceived(body);
        end.expectedBodiesReceived(body);
        expectsMessageCount(0, x, z);

        sendMessage("cheese", body);

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testSendToOtherwiseClause() throws Exception {
        String body = "<three/>";
        z.expectedBodiesReceived(body);
        end.expectedBodiesReceived(body);
        expectsMessageCount(0, x, y);

        sendMessage("somethingUndefined", body);

        assertMockEndpointsSatisfied();
    }

    protected void sendMessage(final Object headerValue, final Object body) throws Exception {
        template.sendBodyAndHeader("direct:start", body, "foo", headerValue);
    }

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        x = getMockEndpoint("mock:x");
        y = getMockEndpoint("mock:y");
        z = getMockEndpoint("mock:z");
        end = getMockEndpoint("mock:end");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start").choice().when().xpath("$foo = 'bar'").to("mock:x").when().xpath("$foo = 'cheese'").to("mock:y").otherwise().to("mock:z").end().to("mock:end");
            }
        };
    }

}
