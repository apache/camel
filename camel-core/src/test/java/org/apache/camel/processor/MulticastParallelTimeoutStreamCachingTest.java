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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Before;
import org.junit.Test;

public class MulticastParallelTimeoutStreamCachingTest extends ContextTestSupport {

    private static final String TARGET_MULTICAST_PARALLEL_TIMEOUT_STREAM_CACHING_TEST_CACHE = "target/MulticastParallelTimeoutStreamCachingTestCache";
    private static final String bodyString = "message body";
    private static final byte[] BODY = bodyString.getBytes(StandardCharsets.UTF_8);

    protected Endpoint startEndpoint;
    protected MockEndpoint x;

    public static void deleteDirectory(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File child : files) {
                deleteDirectory(child);
            }
        }

        file.delete();
    }

    @Test
    public void testSendingAMessageUsingMulticastConvertsToReReadable() throws Exception {
        x.expectedBodiesReceived(bodyString);

        template.sendBody("direct:a", "testMessage");
        assertMockEndpointsSatisfied();

        File f = new File(TARGET_MULTICAST_PARALLEL_TIMEOUT_STREAM_CACHING_TEST_CACHE);
        assertTrue(f.isDirectory());
        Thread.sleep(500l); // deletion happens asynchron
        File[] files = f.listFiles();
        assertEquals(0, files.length);
    }

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();

        deleteDirectory(new File(TARGET_MULTICAST_PARALLEL_TIMEOUT_STREAM_CACHING_TEST_CACHE));
        x = getMockEndpoint("mock:x");
    }

    protected RouteBuilder createRouteBuilder() {
        final Processor processor1 = new Processor() {
            public void process(Exchange exchange) {
                try {
                    // sleep for one second so that the stream cache is built after the main exchange has finished due to timeout on the multicast
                    Thread.sleep(1000l);
                } catch (InterruptedException e) {
                    throw new IllegalStateException("Unexpected exception", e);
                }
                Message in = exchange.getIn();
                // use FilterInputStream to trigger streamcaching
                in.setBody(new FilterInputStream(new ByteArrayInputStream(BODY)) {

                });
            }
        };

        return new RouteBuilder() {
            public void configure() {
                // enable stream caching
                context.getStreamCachingStrategy().setSpoolDirectory(TARGET_MULTICAST_PARALLEL_TIMEOUT_STREAM_CACHING_TEST_CACHE);
                context.getStreamCachingStrategy().setEnabled(true);
                context.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(false);
                context.getStreamCachingStrategy().setSpoolThreshold(1l);
                context.setStreamCaching(true);

                from("direct:a").multicast().timeout(500l).parallelProcessing().to("direct:x");

                from("direct:x").process(processor1).to("mock:x");
            }
        };
    }
}
