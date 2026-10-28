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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.spi.PropertiesReload;
import org.apache.camel.support.service.ServiceSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CAMEL-25767: a properties file that changed in dev mode is configured from. camel run --source-dir=. knows
 * application.properties as file:./application.properties, and the file watcher reports file:application.properties:
 * the two were compared as text, so the configuration in the file was never applied (only its placeholders, as the file
 * was added once more).
 */
public class RouteWatcherReloadPropertiesLocationTest extends ContextTestSupport {

    @TempDir
    Path dir;

    private final List<String> changes = new ArrayList<>();
    private String known;
    private Path file;

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        file = Files.writeString(dir.resolve("app.properties"), "greeting = hello\n");
        known = "file:" + dir + "/./app.properties";
        context.getPropertiesComponent().setLocation(known);
        context.addService(new RecordingPropertiesReload(changes));
        return context;
    }

    @Test
    public void theFileThatChangedIsTheFileThePropertiesComponentKnows() throws Exception {
        Files.writeString(file, "greeting = hello\ncamel.component.log.exchange-formatter = #bean:myFormatter\n");

        boolean reloaded = strategy().onPropertiesReload(ResourceHelper.resolveResource(context, "file:" + file), false);

        assertTrue(reloaded);
        assertEquals(List.of("changed " + known + ": camel.component.log.exchange-formatter"), changes);
        // the file is not added a second time
        assertEquals(1, context.getPropertiesComponent().getLocations().size());
    }

    @Test
    public void theConfigurationOfANewPropertiesFileIsAppliedToo() throws Exception {
        Path other = Files.writeString(dir.resolve("other.properties"),
                "camel.component.log.exchange-formatter = #bean:myFormatter\n");

        boolean reloaded = strategy().onPropertiesReload(ResourceHelper.resolveResource(context, "file:" + other), false);

        assertTrue(reloaded);
        assertEquals(List.of("changed file:" + other + ": camel.component.log.exchange-formatter"), changes);
        // and its properties resolve
        assertEquals("#bean:myFormatter",
                context.getPropertiesComponent().resolveProperty("camel.component.log.exchange-formatter").orElse(null));
    }

    private RouteWatcherReloadStrategy strategy() {
        RouteWatcherReloadStrategy strategy = new RouteWatcherReloadStrategy();
        strategy.setCamelContext(context);
        return strategy;
    }

    // records what the reload hands to the listener, which camel-main configures from
    private static class RecordingPropertiesReload extends ServiceSupport implements PropertiesReload {

        private final List<String> changes;

        RecordingPropertiesReload(List<String> changes) {
            this.changes = changes;
        }

        @Override
        public void onReload(String name, Properties properties) {
            changes.add("reloaded " + name + ": " + String.join(", ", properties.stringPropertyNames()));
        }

        @Override
        public void onChanged(String name, Properties properties) {
            changes.add("changed " + name + ": " + String.join(", ", properties.stringPropertyNames()));
        }
    }
}
