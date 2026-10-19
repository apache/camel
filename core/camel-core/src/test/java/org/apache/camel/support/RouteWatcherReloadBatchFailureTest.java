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
package org.apache.camel.support;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.impl.engine.DefaultRoutesLoader;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.spi.PropertiesReload;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.RoutesLoader;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25834: the files of one scan of the folder are reloaded together. When a properties file of such a batch failed
 * to reload, the failure was only logged at DEBUG: the batch was reloaded again one file at a time, and by then the
 * properties component had the new values of the file, so the file had no changes left and nothing failed.
 */
public class RouteWatcherReloadBatchFailureTest extends ContextTestSupport {

    private static final String WRONG = "the configuration in the file is wrong";

    @TempDir
    Path dir;

    private final List<String> applied = new CopyOnWriteArrayList<>();
    private final List<CamelEvent.CamelContextReloadFailureEvent> failures = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private Path wrong;
    private Path other;
    private Appender appender;
    private RecordingRoutesLoader loader;
    private RouteWatcherReloadStrategy strategy;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        wrong = Files.writeString(dir.resolve("app.properties"), "greeting = hello\n");
        other = Files.writeString(dir.resolve("other.properties"), "name = world\n");
        context.getPropertiesComponent().setLocation("file:" + wrong + ",file:" + other);
        context.addService(new FailingPropertiesReload("file:" + wrong, applied));
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                if (event instanceof CamelEvent.CamelContextReloadFailureEvent failure) {
                    failures.add(failure);
                }
            }
        });
        return context;
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        loader = new RecordingRoutesLoader();
        loader.setCamelContext(context);
        context.getCamelContextExtension().addContextPlugin(RoutesLoader.class, loader);
        // started without a folder: it has the listener that reloads one file, and watches nothing
        strategy = new RouteWatcherReloadStrategy();
        strategy.setCamelContext(context);
        strategy.start();

        appender = new AbstractAppender("reload-warnings", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().addAppender(appender, Level.ALL, null);
        logging.updateLoggers();
    }

    @AfterEach
    public void stopCapture() {
        strategy.stop();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().removeAppender(appender.getName());
        logging.updateLoggers();
        appender.stop();
    }

    @Test
    public void aPropertiesFileThatFailsIsReportedAndTheRouteStillReloads() throws Exception {
        Files.writeString(wrong, "greeting = hello\ncamel.beans.faker = #class:com.github.javafaker.Faker\n");
        File route = Files.writeString(dir.resolve("route.yaml"), "- route: {}\n").toFile();

        strategy.onReloadBatch(List.of(wrong.toFile(), route));

        assertReported();
        // the route of the batch reloads once, and counts as reloaded
        assertEquals(List.of("file:" + route.getPath()), loader.loaded);
        assertEquals(1, strategy.getReloadCounter());
    }

    @Test
    public void aPropertiesFileThatFailsDoesNotStopTheOtherPropertiesFile() throws Exception {
        Files.writeString(wrong, "greeting = hello\ncamel.beans.faker = #class:com.github.javafaker.Faker\n");
        Files.writeString(other, "name = world\ncamel.component.log.level = WARN\n");

        strategy.onReloadBatch(List.of(wrong.toFile(), other.toFile()));

        assertReported();
        assertEquals(List.of("file:" + other + ": camel.component.log.level"), applied);
        assertEquals(1, strategy.getReloadCounter());
    }

    // reported as when the file is reloaded on its own: a WARN, the failed counter, the last error and the event
    private void assertReported() {
        assertTrue(warnings.stream().anyMatch(w -> w.contains(wrong.toString()) && w.contains(WRONG)),
                "A WARN names the file and why it failed: " + warnings);
        assertEquals(1, strategy.getFailedCounter());
        assertEquals(WRONG, strategy.getLastError().getMessage());
        assertEquals(1, failures.size());
        assertEquals(WRONG, failures.get(0).getCause().getMessage());
    }

    // the listener that applies the configuration of a properties file, as camel-main does: it fails on one file
    private static class FailingPropertiesReload extends ServiceSupport implements PropertiesReload {

        private final String wrong;
        private final List<String> applied;

        FailingPropertiesReload(String wrong, List<String> applied) {
            this.wrong = wrong;
            this.applied = applied;
        }

        @Override
        public void onReload(String name, Properties properties) {
            if (wrong.equals(name)) {
                throw new IllegalArgumentException(WRONG);
            }
            applied.add(name + ": " + String.join(", ", properties.stringPropertyNames()));
        }
    }

    // loads no routes: it records the files
    private static class RecordingRoutesLoader extends DefaultRoutesLoader {
        private final List<String> loaded = new ArrayList<>();

        @Override
        public Set<String> updateRoutes(Collection<Resource> resources) {
            for (Resource resource : resources) {
                loaded.add(resource.getLocation());
            }
            return Set.of();
        }
    }
}
