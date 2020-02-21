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

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class WireTapNoCacheTest extends ContextTestSupport {

    @Test
    public void testNoCache() throws Exception {
        assertEquals(1, context.getEndpointRegistry().size());

        sendBody("foo", "mock:x");
        sendBody("foo", "mock:y");
        sendBody("foo", "mock:z");
        sendBody("bar", "mock:x");
        sendBody("bar", "mock:y");
        sendBody("bar", "mock:z");

        // make sure its using an empty producer cache as the cache is disabled
        List<Processor> list = context.getRoute("route1").filter("foo");
        WireTapProcessor wtp = (WireTapProcessor) list.get(0);
        assertNotNull(wtp);
        assertEquals(-1, wtp.getCacheSize());

        // check no additional endpoints added as cache was disabled
        assertEquals(1, context.getEndpointRegistry().size());

        // now send again with mocks which then add endpoints

        MockEndpoint x = getMockEndpoint("mock:x2");
        MockEndpoint y = getMockEndpoint("mock:y2");
        MockEndpoint z = getMockEndpoint("mock:z2");

        x.expectedBodiesReceivedInAnyOrder("foo", "bar");
        y.expectedBodiesReceivedInAnyOrder("foo", "bar");
        z.expectedBodiesReceivedInAnyOrder("foo", "bar");

        assertEquals(4, context.getEndpointRegistry().size());

        sendBody("foo", "mock:x2");
        sendBody("foo", "mock:y2");
        sendBody("foo", "mock:z2");
        sendBody("bar", "mock:x2");
        sendBody("bar", "mock:y2");
        sendBody("bar", "mock:z2");

        // should not register as new endpoint so we keep at 4
        sendBody("dummy", "mock:dummy");

        assertMockEndpointsSatisfied();

        assertEquals(4, context.getEndpointRegistry().size());
    }

    protected void sendBody(String body, String uri) {
        template.sendBodyAndHeader("direct:a", body, "myHeader", uri);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:a")
                        .wireTap("${header.myHeader}").cacheSize(-1).id("foo").end();
            }
        };

    }

}
