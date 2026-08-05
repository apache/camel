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
package org.apache.camel.converter.stream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.impl.engine.DefaultStreamCachingStrategy;
import org.apache.camel.impl.engine.DefaultUnitOfWork;
import org.apache.camel.spi.UnitOfWork;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.util.IOHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for per-Exchange spool directory resolution via
 * {@link org.apache.camel.spi.StreamCachingStrategy#resolveSpoolDirectory(Exchange)}.
 */
class CachedOutputStreamPerExchangeSpoolDirTest extends ContextTestSupport {

    private static final String TEST_STRING = "This is a test string that is long enough to exceed the spool threshold"
                                              + " aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa ";

    private Exchange exchange;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.setStreamCaching(true);
        context.getStreamCachingStrategy().setSpoolDirectory(testDirectory().toFile());
        context.getStreamCachingStrategy().setSpoolEnabled(true);
        context.getStreamCachingStrategy().setSpoolThreshold(16);
        return context;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        exchange = new DefaultExchange(context);
        UnitOfWork uow = new DefaultUnitOfWork(exchange);
        exchange.getExchangeExtension().setUnitOfWork(uow);
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    private static String toString(InputStream input) throws IOException {
        BufferedReader reader = IOHelper.buffered(new InputStreamReader(input));
        StringJoiner builder = new StringJoiner(", ");
        while (true) {
            String line = reader.readLine();
            if (line == null) {
                return builder.toString();
            }
            builder.add(line);
        }
    }

    @Test
    void testDefaultResolveSpoolDirectoryReturnsSameDirectory() throws Exception {
        context.start();

        // The default resolveSpoolDirectory should return the same directory as getSpoolDirectory
        File spoolDir = context.getStreamCachingStrategy().getSpoolDirectory();
        File resolvedDir = context.getStreamCachingStrategy().resolveSpoolDirectory(exchange);
        assertThat(resolvedDir).isEqualTo(spoolDir);

        // Verify spooling still works with default behavior
        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        StreamCache cache = cos.newStreamCache();
        assertThat(cache).isInstanceOf(FileInputStreamCache.class);
        String content = toString((InputStream) cache);
        assertThat(content).isEqualTo(TEST_STRING);

        ((InputStream) cache).close();
        exchange.getUnitOfWork().done(exchange);
        IOHelper.close(cos);
    }

    @Test
    void testPerExchangeSpoolDirectoryResolution() throws Exception {
        // Use a custom strategy that returns a per-route subdirectory
        File baseDir = testDirectory().toFile();
        DefaultStreamCachingStrategy customStrategy = new DefaultStreamCachingStrategy() {
            @Override
            public File resolveSpoolDirectory(Exchange exchange) {
                String routeId = exchange.getFromRouteId();
                if (routeId != null) {
                    return new File(getSpoolDirectory(), routeId);
                }
                return getSpoolDirectory();
            }
        };
        customStrategy.setCamelContext(context);
        customStrategy.setEnabled(true);
        customStrategy.setSpoolEnabled(true);
        customStrategy.setSpoolDirectory(baseDir);
        customStrategy.setSpoolThreshold(16);
        context.setStreamCachingStrategy(customStrategy);

        context.start();

        // Create an exchange that simulates coming from a route
        Exchange exchangeWithRoute = new DefaultExchange(context);
        exchangeWithRoute.getExchangeExtension().setFromRouteId("routeA");
        UnitOfWork uow = new DefaultUnitOfWork(exchangeWithRoute);
        exchangeWithRoute.getExchangeExtension().setUnitOfWork(uow);

        CachedOutputStream cos = new CachedOutputStream(exchangeWithRoute);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        // The temp file should be in the per-route subdirectory
        StreamCache cache = cos.newStreamCache();
        assertThat(cache).isInstanceOf(FileInputStreamCache.class);

        // Verify the subdirectory was created
        File routeDir = new File(baseDir, "routeA");
        assertThat(routeDir).exists().isDirectory();

        // Verify files are in the per-route subdirectory
        String[] files = routeDir.list();
        assertThat(files).isNotNull().hasSize(1);
        assertThat(files[0]).startsWith("cos");

        // Verify content is correct
        String content = toString((InputStream) cache);
        assertThat(content).isEqualTo(TEST_STRING);

        ((InputStream) cache).close();
        exchangeWithRoute.getUnitOfWork().done(exchangeWithRoute);
        IOHelper.close(cos);
    }

    @Test
    void testPerExchangeSpoolDirWithDifferentRoutes() throws Exception {
        // Use a custom strategy that returns a per-route subdirectory
        File baseDir = testDirectory().toFile();
        DefaultStreamCachingStrategy customStrategy = new DefaultStreamCachingStrategy() {
            @Override
            public File resolveSpoolDirectory(Exchange exchange) {
                String routeId = exchange.getFromRouteId();
                if (routeId != null) {
                    return new File(getSpoolDirectory(), routeId);
                }
                return getSpoolDirectory();
            }
        };
        customStrategy.setCamelContext(context);
        customStrategy.setEnabled(true);
        customStrategy.setSpoolEnabled(true);
        customStrategy.setSpoolDirectory(baseDir);
        customStrategy.setSpoolThreshold(16);
        context.setStreamCachingStrategy(customStrategy);

        context.start();

        // Create exchange for route A
        Exchange exchangeA = new DefaultExchange(context);
        exchangeA.getExchangeExtension().setFromRouteId("routeA");
        UnitOfWork uowA = new DefaultUnitOfWork(exchangeA);
        exchangeA.getExchangeExtension().setUnitOfWork(uowA);

        CachedOutputStream cosA = new CachedOutputStream(exchangeA);
        cosA.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        // Create exchange for route B
        Exchange exchangeB = new DefaultExchange(context);
        exchangeB.getExchangeExtension().setFromRouteId("routeB");
        UnitOfWork uowB = new DefaultUnitOfWork(exchangeB);
        exchangeB.getExchangeExtension().setUnitOfWork(uowB);

        CachedOutputStream cosB = new CachedOutputStream(exchangeB);
        cosB.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        // Verify both subdirectories were created and contain spool files
        File routeADir = new File(baseDir, "routeA");
        File routeBDir = new File(baseDir, "routeB");

        assertThat(routeADir).exists().isDirectory();
        assertThat(routeBDir).exists().isDirectory();

        String[] filesA = routeADir.list();
        String[] filesB = routeBDir.list();
        assertThat(filesA).isNotNull().hasSize(1);
        assertThat(filesB).isNotNull().hasSize(1);

        // Verify content of both streams
        StreamCache cacheA = cosA.newStreamCache();
        StreamCache cacheB = cosB.newStreamCache();
        assertThat(toString((InputStream) cacheA)).isEqualTo(TEST_STRING);
        assertThat(toString((InputStream) cacheB)).isEqualTo(TEST_STRING);

        ((InputStream) cacheA).close();
        ((InputStream) cacheB).close();
        exchangeA.getUnitOfWork().done(exchangeA);
        exchangeB.getUnitOfWork().done(exchangeB);
        IOHelper.close(cosA);
        IOHelper.close(cosB);
    }

    @Test
    void testPerExchangeSpoolDirFallsBackWhenNoRouteId() throws Exception {
        // Use a custom strategy that returns a per-route subdirectory
        File baseDir = testDirectory().toFile();
        DefaultStreamCachingStrategy customStrategy = new DefaultStreamCachingStrategy() {
            @Override
            public File resolveSpoolDirectory(Exchange exchange) {
                String routeId = exchange.getFromRouteId();
                if (routeId != null) {
                    return new File(getSpoolDirectory(), routeId);
                }
                return getSpoolDirectory();
            }
        };
        customStrategy.setCamelContext(context);
        customStrategy.setEnabled(true);
        customStrategy.setSpoolEnabled(true);
        customStrategy.setSpoolDirectory(baseDir);
        customStrategy.setSpoolThreshold(16);
        context.setStreamCachingStrategy(customStrategy);

        context.start();

        // Exchange without fromRouteId should fall back to base directory
        CachedOutputStream cos = new CachedOutputStream(exchange);
        cos.write(TEST_STRING.getBytes(StandardCharsets.UTF_8));

        StreamCache cache = cos.newStreamCache();
        assertThat(cache).isInstanceOf(FileInputStreamCache.class);

        // Files should be in the base directory (no subdirectory)
        String[] files = baseDir.list();
        assertThat(files).isNotNull().hasSizeGreaterThanOrEqualTo(1);

        String content = toString((InputStream) cache);
        assertThat(content).isEqualTo(TEST_STRING);

        ((InputStream) cache).close();
        exchange.getUnitOfWork().done(exchange);
        IOHelper.close(cos);
    }
}
