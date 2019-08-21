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

import java.io.StringReader;

import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Test;

public class StreamCachingPerRouteTest extends ContextTestSupport {

    @Test
    public void testStreamCachingPerRoute() throws Exception {
        MockEndpoint a = getMockEndpoint("mock:a");
        a.expectedMessageCount(1);

        MockEndpoint b = getMockEndpoint("mock:b");
        b.expectedMessageCount(1);

        MockEndpoint c = getMockEndpoint("mock:c");
        c.expectedMessageCount(1);

        new StreamSource(new StringReader("A"));

        template.sendBody("direct:a", new StreamSource(new StringReader("A")));
        Object sendB = new StreamSource(new StringReader("B"));
        template.sendBody("direct:b", sendB);
        template.sendBody("direct:c", new StreamSource(new StringReader("C")));

        assertMockEndpointsSatisfied();

        Object bodyA = a.getReceivedExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(StreamCache.class, bodyA);

        Object bodyC = c.getReceivedExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(StreamCache.class, bodyC);

        // should not be stream cache but the pure body
        Object bodyB = b.getReceivedExchanges().get(0).getIn().getBody();
        assertIsInstanceOf(StreamSource.class, bodyB);
        assertSame("Should be same body as we send", sendB, bodyB);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.setStreamCaching(true);

                from("direct:a").to("mock:a");

                from("direct:b").noStreamCaching().to("mock:b");

                from("direct:c").streamCaching().to("mock:c");
            }
        };
    }
}
