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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.stream.CachedOutputStream;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 *
 */
@DisabledOnOs(architectures = { "s390x" },
              disabledReason = "This test does not run reliably on s390x")
public class MulticastParallelTimeoutStreamCachingTest extends ContextTestSupport {

    private static final String BODY_STRING = "message body";
    private static final byte[] BODY = BODY_STRING.getBytes(StandardCharsets.UTF_8);

    @Test
    public void testCreateOutputStreamCacheAfterTimeout() throws Exception {
        getMockEndpoint("mock:x").expectedBodiesReceived(BODY_STRING);

        template.sendBody("direct:a", "testMessage");
        assertMockEndpointsSatisfied();

        File f = testDirectory().toFile();
        assertTrue(f.isDirectory());
        Awaitility.await().atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    File[] files = f.listFiles();
                    assertNotNull(files, "There should be a list of files");
                    assertEquals(0, files.length);
                });
    }

    @Test
    public void testCreateOutputStreamCacheBeforeTimeoutButWriteToOutputStreamCacheAfterTimeout() throws Exception {
        getMockEndpoint("mock:exception").expectedMessageCount(1);
        getMockEndpoint("mock:exception").setResultWaitTime(15000);
        getMockEndpoint("mock:y").expectedMessageCount(0);
        getMockEndpoint("mock:y").setAssertPeriod(2000);

        template.sendBody("direct:b", "testMessage");
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        final Processor setStreamBody = new Processor() {
            public void process(Exchange exchange) {
                Message in = exchange.getIn();
                // use FilterInputStream to trigger streamcaching
                in.setBody(new FilterInputStream(new ByteArrayInputStream(BODY)) {

                });
            }
        };

        final Processor createOutputStream = new Processor() {
            public void process(Exchange exchange) {
                CachedOutputStream outputStream = new CachedOutputStream(exchange);
                exchange.setProperty("cachedOutputStream", outputStream);
            }
        };

        final Processor writeOutputStream = new Processor() {
            public void process(Exchange exchange) throws IOException {
                CachedOutputStream outputStream = exchange.getProperty("cachedOutputStream", CachedOutputStream.class);
                outputStream.write(BODY);
                Message in = exchange.getIn();
                // use FilterInputStream to trigger streamcaching
                in.setBody(outputStream.getInputStream());
            }
        };

        return new RouteBuilder() {
            public void configure() {
                // enable stream caching
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setEnabled(true);
                context.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(false);
                context.getStreamCachingStrategy().setSpoolThreshold(1L);
                context.setStreamCaching(true);

                onException(IOException.class).to("mock:exception");

                from("direct:a").multicast().timeout(2000).parallelProcessing().to("direct:x");

                // delay so the stream cache is built after the main exchange has finished due to timeout
                from("direct:x").delay(5000).process(setStreamBody).to("mock:x");

                from("direct:b").multicast().timeout(2000).parallelProcessing().to("direct:y");

                // create the CachedOutputStream before the delay, then write to it after
                // the delay (which is after the multicast timeout), which should cause an IOException
                from("direct:y").process(createOutputStream).delay(5000).process(writeOutputStream).to("mock:y");
            }
        };
    }
}
