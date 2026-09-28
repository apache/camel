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
package org.apache.camel.component.properties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.LoadablePropertiesSource;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.spi.PropertiesResolvedValue;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.OrderedLocationProperties;
import org.apache.camel.util.OrderedProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PropertiesComponentEdgeCasesTest {

    static final AtomicInteger CREATED = new AtomicInteger();

    @TempDir
    Path dir;

    public static class CountingFunction extends ServiceSupport implements PropertiesFunction {
        public CountingFunction() {
            CREATED.incrementAndGet();
        }

        @Override
        public String getName() {
            return "edgecount";
        }

        @Override
        public String apply(String remainder) {
            return remainder;
        }
    }

    private static class MySource implements LoadablePropertiesSource {
        private final AtomicInteger loadAll = new AtomicInteger();

        @Override
        public String getName() {
            return "mySource";
        }

        @Override
        public String getProperty(String name) {
            return "foo".equals(name) ? "bar" : null;
        }

        @Override
        public Properties loadProperties() {
            loadAll.incrementAndGet();
            Properties answer = new OrderedProperties();
            answer.put("foo", "bar");
            return answer;
        }

        @Override
        public Properties loadProperties(Predicate<String> filter) {
            Properties answer = new OrderedProperties();
            if (filter.test("foo")) {
                answer.put("foo", "bar");
            }
            return answer;
        }

        @Override
        public void reloadProperties(String location) {
            // noop
        }
    }

    @Test
    public void testReloadIsAtomic() throws Exception {
        Path file = dir.resolve("my.properties");
        Files.writeString(file, "greeting=Hello\n");

        CamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setLocation("file:" + file);
        context.start();
        try {
            AtomicBoolean done = new AtomicBoolean();
            AtomicInteger misses = new AtomicInteger();
            Thread reader = new Thread(() -> {
                while (!done.get()) {
                    if (context.getPropertiesComponent().resolveProperty("greeting").isEmpty()) {
                        misses.incrementAndGet();
                    }
                }
            });
            reader.start();
            for (int i = 0; i < 2000; i++) {
                context.getPropertiesComponent().reloadProperties("*");
            }
            done.set(true);
            reader.join(TimeUnit.SECONDS.toMillis(10));
            assertEquals(0, misses.get());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testAddPropertiesSourceWhileLookup() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().addInitialProperty("greeting", "Hello");
        context.start();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            AtomicBoolean done = new AtomicBoolean();
            Future<?> reader = executor.submit(() -> {
                while (!done.get()) {
                    context.getPropertiesComponent().resolveProperty("greeting");
                }
                return null;
            });
            for (int i = 0; i < 2000; i++) {
                context.getPropertiesComponent().addPropertiesSource(new MySource());
            }
            done.set(true);
            assertDoesNotThrow(() -> reader.get(10, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
            context.stop();
        }
    }

    @Test
    public void testLookupDoesNotLoadAllProperties() throws Exception {
        MySource source = new MySource();
        CamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().addPropertiesSource(source);
        context.start();
        try {
            for (int i = 0; i < 10; i++) {
                assertEquals("bar", context.resolvePropertyPlaceholders("{{foo}}"));
            }
            assertEquals(0, source.loadAll.get());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testReloadOptionalLocation() throws Exception {
        Path file = dir.resolve("optional.properties");
        Files.writeString(file, "greeting=Hello\n");

        CamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setLocation("file:" + file + ";optional=true");
        context.start();
        try {
            Files.delete(file);
            assertDoesNotThrow(() -> context.getPropertiesComponent().reloadProperties("*"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testUnknownLocationResolver() {
        CamelContext context = new DefaultCamelContext();
        context.getPropertiesComponent().setLocation("Classpath:does-not-exist.properties");
        assertThrows(Exception.class, context::start);

        CamelContext context2 = new DefaultCamelContext();
        context2.getPropertiesComponent().setLocation("Classpath:does-not-exist.properties;optional=true");
        assertDoesNotThrow(context2::start);
        context2.stop();
    }

    @Test
    public void testAddLocation() throws Exception {
        Path file = dir.resolve("added.properties");
        Files.writeString(file, "added=yes\n");

        CamelContext context = new DefaultCamelContext();
        PropertiesComponent pc = (PropertiesComponent) context.getPropertiesComponent();
        pc.setLocation("classpath:org/apache/camel/component/properties/myproperties.properties");
        pc.addLocation(new PropertiesLocation("file:" + file));
        context.start();
        try {
            assertEquals("yes", context.resolvePropertyPlaceholders("{{added}}"));
        } finally {
            context.stop();
        }
    }

    @Test
    public void testResolveFunctionOnce() throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.start();
        CREATED.set(0);
        ExecutorService executor = Executors.newFixedThreadPool(16);
        try {
            CountDownLatch latch = new CountDownLatch(1);
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < 16; i++) {
                futures.add(executor.submit(() -> {
                    latch.await();
                    return context.resolvePropertyPlaceholders("{{edgecount:a}}");
                }));
            }
            latch.countDown();
            for (Future<String> f : futures) {
                assertEquals("a", f.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1, CREATED.get());
        } finally {
            executor.shutdownNow();
            context.stop();
        }
    }

    @Test
    public void testResolvedValueBeforeBuild() {
        PropertiesComponent pc = new PropertiesComponent();
        assertDoesNotThrow(() -> pc.getResolvedValue("x"));
        assertFalse(pc.getResolvedValue("x").isPresent());
    }

    @Test
    public void testOverridePropertiesLocation() throws Exception {
        CamelContext context = new DefaultCamelContext();
        PropertiesComponent pc = (PropertiesComponent) context.getPropertiesComponent();
        OrderedLocationProperties override = new OrderedLocationProperties();
        override.put("myloc", "greeting", "Hello");
        pc.setOverrideProperties(override);
        context.start();
        try {
            assertEquals("Hello", context.resolvePropertyPlaceholders("{{greeting}}"));
            PropertiesResolvedValue value = pc.getResolvedValue("greeting").get();
            assertEquals("myloc", value.source());
        } finally {
            context.stop();
        }
    }

    @Test
    public void testLocationWithSemicolonInPath() {
        PropertiesLocation location = new PropertiesLocation("file:/a;b/c.properties");
        assertEquals("/a;b/c.properties", location.getPath());
        assertFalse(location.isOptional());

        location = new PropertiesLocation("file:/a/c.properties;optional=true");
        assertEquals("/a/c.properties", location.getPath());
        assertTrue(location.isOptional());
    }
}
