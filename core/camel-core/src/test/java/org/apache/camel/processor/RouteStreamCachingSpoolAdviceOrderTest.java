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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Regression test for CAMEL-26640: route-level StreamCachingAdvice fails the exchange with "Cannot reset stream from
 * file" when the spooled stream cache is used.
 *
 * <p>
 * Root cause: StreamCachingAdvice.getOrder() was Ordered.HIGHEST (Integer.MIN_VALUE), which made it sort first in the
 * advice list. Since after() runs in reverse order, UnitOfWorkProcessorAdvice.after() (which triggers TempFileManager's
 * onCompletion that deletes the spool file) ran BEFORE StreamCachingAdvice.after() (which tried to reset the
 * FileInputStreamCache from the now-deleted file).
 *
 * <p>
 * Fix: UnitOfWorkProcessorAdvice now implements {@link org.apache.camel.Ordered} and returns {@code Ordered.HIGHEST}
 * (Integer.MIN_VALUE). StreamCachingAdvice returns {@code Ordered.HIGHEST + 1}. This gives:
 * <ul>
 * <li>before(): UoW → StreamCaching → tracers/debuggers (stream is cached before being read by tracers)</li>
 * <li>after(): tracers → StreamCaching → UoW (stream is reset before TempFileManager deletes the spool file)</li>
 * </ul>
 *
 * <p>
 * The body is intentionally NOT converted with {@code .convertBodyTo()} so it stays as a {@link StreamCache} through
 * the after() phase. Converting to byte[] would replace the StreamCache body before after() runs, making
 * {@code MessageHelper.resetStreamCache()} a no-op and masking the bug.
 */
class RouteStreamCachingSpoolAdviceOrderTest extends ContextTestSupport {

    // 32 KB — well above the 1 KB spool threshold set in createRouteBuilder
    private static final byte[] DATA = createData(32 * 1024);

    @Test
    void testRouteLevelSpooledStreamCacheDoesNotFailExchange() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        // Body must arrive at mock:result as a StreamCache (not converted), confirming it was
        // spooled and that StreamCachingAdvice.after() will call reset() on a FileInputStreamCache.
        // Without the fix that call throws "Cannot reset stream from file" because
        // UnitOfWorkProcessorAdvice.after() already deleted the spool file, failing the exchange
        // and preventing it from reaching mock:result at all.
        result.message(0).body().isInstanceOf(StreamCache.class);

        // Send a stream that will be spooled to disk (exceeds the spool threshold)
        template.sendBody("direct:test", stream());

        assertMockEndpointsSatisfied();
        Exchange exchange = result.getReceivedExchanges().get(0);
        assertNull(exchange.getException());

        // Spool file must have been cleaned up by TempFileManager after the exchange completes.
        // The spool directory itself is kept (RemoveSpoolDirectoryWhenStopping=false) but it
        // must contain no .cos*.tmp files — TempFileManager deletes each file in onCompletion.
        Path spoolDir = testDirectory();
        long spoolFiles = Files.list(spoolDir)
                .filter(p -> p.getFileName().toString().startsWith("cos") && p.getFileName().toString().endsWith(".tmp"))
                .count();
        assertThat(spoolFiles).as("spool files left after exchange completion").isZero();
    }

    @Test
    void testBodyCanBeReadDuringRouteProcessing() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);

        template.sendBody("direct:test-read", stream());

        assertMockEndpointsSatisfied();
        // The processor read the body bytes while still in-route and stored them in a property
        byte[] received = result.getReceivedExchanges().get(0).getProperty("bodyBytes", byte[].class);
        assertThat(received).isEqualTo(DATA);
        assertNull(result.getReceivedExchanges().get(0).getException());
    }

    private static InputStream stream() {
        // BufferedInputStream wrapping ByteArrayInputStream is treated as a re-readable stream
        // by the type converter; it will be spooled to disk when it exceeds spoolThreshold
        return new BufferedInputStream(new ByteArrayInputStream(DATA));
    }

    private static byte[] createData(int size) {
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = (byte) ('a' + i % 26);
        }
        return data;
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // Route-level stream caching with spooling enabled and a low threshold so the
                // 32 KB body is always spooled to disk rather than held in memory.
                context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
                context.getStreamCachingStrategy().setSpoolEnabled(true);
                context.getStreamCachingStrategy().setSpoolThreshold(1024);
                context.getStreamCachingStrategy().setRemoveSpoolDirectoryWhenStopping(false);

                // Route 1: body stays as StreamCache — StreamCachingAdvice.after() will call reset()
                // on a FileInputStreamCache. Without the fix this throws "Cannot reset stream from
                // file" and the exchange fails before reaching mock:result.
                from("direct:test")
                        .streamCache(true)
                        .to("mock:result");

                // Route 2: a processor reads the body bytes while still in-route (spool file alive),
                // stores them in an exchange property, and keeps the body as StreamCache so that
                // after() still exercises the reset path.
                from("direct:test-read")
                        .streamCache(true)
                        .process(exchange -> {
                            byte[] bytes = exchange.getMessage().getBody(byte[].class);
                            exchange.setProperty("bodyBytes", bytes);
                            // Confirm the body is still a StreamCache after the read
                            assertInstanceOf(StreamCache.class, exchange.getMessage().getBody());
                        })
                        .to("mock:result");
            }
        };
    }
}
