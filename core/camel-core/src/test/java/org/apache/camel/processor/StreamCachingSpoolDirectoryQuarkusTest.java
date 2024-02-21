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
import java.io.FilterInputStream;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ManagementNameStrategy;
import org.apache.camel.spi.StreamCachingStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StreamCachingSpoolDirectoryQuarkusTest extends ContextTestSupport {

    private MyCustomSpoolRule spoolRule = new MyCustomSpoolRule();

    private static class MyCamelContext extends DefaultCamelContext {

        public MyCamelContext(boolean init) {
            super(init);
        }

        @Override
        public ManagementNameStrategy getManagementNameStrategy() {
            // quarkus has no management at all
            return null;
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        DefaultCamelContext context = new MyCamelContext(false);
        context.disableJMX();
        context.getCamelContextExtension().setRegistry(createRegistry());
        context.setLoadTypeConverters(isLoadTypeConverters());
        return context;
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

    private static final class MyInputStream extends FilterInputStream {

        private MyInputStream(InputStream in) {
            super(in);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().addSpoolRule(spoolRule);
                context.getStreamCachingStrategy().setAnySpoolRules(true);
                context.setStreamCaching(true);

                from("direct:a").choice().when(xpath("//hello")).to("mock:english").when(xpath("//hallo"))
                        .to("mock:dutch", "mock:german").otherwise().to("mock:french").end()
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                // check if spool file exists
                                if (spoolRule.isSpool()) {
                                    String[] names = testDirectory().toFile().list();
                                    assertEquals(1, names.length, "There should be a cached spool file");
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
