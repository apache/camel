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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.StreamCachingStrategy;
import org.junit.Before;
import org.junit.Test;

public class StreamCachingCustomShouldSpoolRuleTest extends ContextTestSupport {

    private MyCustomSpoolRule spoolRule = new MyCustomSpoolRule();

    @Override
    @Before
    public void setUp() throws Exception {
        deleteDirectory("target/data/cachedir");
        super.setUp();
    }

    @Test
    public void testByteArrayInputStream() throws Exception {
        getMockEndpoint("mock:english").expectedBodiesReceived("<hello/>");
        getMockEndpoint("mock:dutch").expectedBodiesReceived("<hallo/>");
        getMockEndpoint("mock:german").expectedBodiesReceived("<hallo/>");
        getMockEndpoint("mock:french").expectedBodiesReceived("<hellos/>");

        // need to wrap in MyInputStream as ByteArrayInputStream is optimized to
        // just reuse in memory buffer
        // and not needed to spool to disk
        template.sendBody("direct:a", new MyInputStream(new ByteArrayInputStream("<hello/>".getBytes())));

        spoolRule.setSpool(true);
        template.sendBody("direct:a", new MyInputStream(new ByteArrayInputStream("<hallo/>".getBytes())));
        template.sendBody("direct:a", new MyInputStream(new ByteArrayInputStream("<hellos/>".getBytes())));

        assertMockEndpointsSatisfied();
    }

    private final class MyInputStream extends FilterInputStream {

        private MyInputStream(InputStream in) {
            super(in);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getStreamCachingStrategy().setSpoolDirectory("target/cachedir");
                context.getStreamCachingStrategy().addSpoolRule(spoolRule);
                context.getStreamCachingStrategy().setAnySpoolRules(true);
                context.setStreamCaching(true);

                from("direct:a").choice().when(xpath("//hello")).to("mock:english").when(xpath("//hallo")).to("mock:dutch", "mock:german").otherwise().to("mock:french").end()
                    .process(new Processor() {
                        @Override
                        public void process(Exchange exchange) throws Exception {
                            // check if spool file exists
                            if (spoolRule.isSpool()) {
                                String[] names = new File("target/cachedir").list();
                                assertEquals("There should be a cached spool file", 1, names.length);
                            }
                        }
                    });

            }
        };
    }

    private static final class MyCustomSpoolRule implements StreamCachingStrategy.SpoolRule {

        private volatile boolean spool;

        @Override
        public boolean shouldSpoolCache(long length) {
            return spool;
        }

        public boolean isSpool() {
            return spool;
        }

        public void setSpool(boolean spool) {
            this.spool = spool;
        }

        @Override
        public String toString() {
            return "MyCustomSpoolRule";
        }
    }
}
