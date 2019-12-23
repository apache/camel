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
import org.apache.camel.impl.engine.DefaultStreamCachingStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.junit.Before;
import org.junit.Test;

public class SplitterWireTapStreamCacheTest extends ContextTestSupport {

    private MockEndpoint startEnd;
    private MockEndpoint splitEnd;
    private MockEndpoint wiretapEnd;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        startEnd = getMockEndpoint("mock:startEnd");
        splitEnd = getMockEndpoint("mock:splitEnd");
        wiretapEnd = getMockEndpoint("mock:wireTapEnd");
    }

    @Test
    public void testWireTapAfterSplitDeletesStreamCacheFileWhenSplitFinishes() throws Exception {
        startEnd.expectedMessageCount(1);
        splitEnd.expectedMessageCount(1);
        wiretapEnd.expectedMessageCount(1);

        // test.txt should contain more than one character
        template.sendBody("direct:start", "text");

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                StreamCachingStrategy streamCachingStrategy = new DefaultStreamCachingStrategy();
                streamCachingStrategy.setSpoolThreshold(1L);

                context.setStreamCachingStrategy(streamCachingStrategy);
                context.setStreamCaching(true);

                from("direct:start").split(bodyAs(String.class).tokenize()).to("direct:split").to("mock:startEnd").end();

                from("direct:split").wireTap("direct:wireTap")
                    // wait for the streamcache to be created in the wireTap
                    // route
                    .delay(1000)
                    // spool file is deleted when this route ends
                    .to("mock:splitEnd");

                from("direct:wireTap")
                    // create streamcache
                    .setBody(constant(this.getClass().getResourceAsStream("/log4j2.properties"))).delay(3000)
                    // spool file is deleted by the split route
                    .to("mock:wireTapEnd");
            }
        };
    }
}
