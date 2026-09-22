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
package org.apache.camel.dsl.jbang.core.commands.ai;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.camel.catalog.CamelCatalog;
import org.apache.camel.catalog.DefaultCamelCatalog;
import org.apache.camel.dsl.yaml.common.exception.YamlDeserializationException;
import org.apache.camel.impl.event.CamelContextReloadFailureEvent;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.SimpleEventNotifierSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When a YAML route file fails to load, the loader's message says which node is wrong but not what to write; the schema
 * validator has the message that does, with its hints. This runs the validator on the route files of a failed start (or
 * of a failed reload in dev mode) and prints its report, so the hints reach everyone who runs, whether they validated
 * first or not (CAMEL-24851).
 */
public final class YamlLoadFailureReport {

    private static final Logger LOG = LoggerFactory.getLogger(YamlLoadFailureReport.class);

    private static volatile CamelCatalog catalog;

    private YamlLoadFailureReport() {
    }

    /** Whether the failure comes from loading a YAML route file: a deserialization error or a pre-parse error. */
    public static boolean isYamlLoadFailure(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof YamlDeserializationException) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null && (msg.startsWith("Error pre-parsing resource") || msg.contains("Error constructing YAML node")
                    || msg.contains("Error parsing YAML"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Logs the validator's report for the YAML files of a run whose start failed on loading one of them; nothing for
     * any other failure. Logged (not printed) so that it is in the console and in the run's log file alike, where the
     * camel-jbang-mcp tools read it, and before the loader's own error.
     */
    public static void logIfYamlLoadFailure(Throwable failure, List<String> files) {
        if (!isYamlLoadFailure(failure)) {
            return;
        }
        List<String> lines = report(yamlFiles(files));
        if (!lines.isEmpty()) {
            LOG.error(String.join(System.lineSeparator(), lines));
        }
    }

    /**
     * The validator's report for the YAML files, as lines to print: a header, then per file with problems its name and
     * the messages, then a footer; empty when the validator finds nothing (then the loader's message is all there is).
     */
    public static List<String> report(List<Path> yamlFiles) {
        List<String> lines = new ArrayList<>();
        for (Path file : yamlFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            List<String> errors;
            try {
                String content = Files.readString(file);
                Path dir = file.toAbsolutePath().getParent();
                errors = SourceValidator.validate(file.getFileName().toString(), content, catalog(), null, dir);
            } catch (Exception e) {
                // the validator must never hide the loader's error
                LOG.debug("Cannot validate {}", file, e);
                continue;
            }
            if (!errors.isEmpty()) {
                if (lines.isEmpty()) {
                    lines.add("The route file did not load. camel validate yaml says what to write:");
                }
                lines.add("  " + file.getFileName() + ":");
                for (String error : errors) {
                    lines.add("    " + error);
                }
            }
        }
        if (!lines.isEmpty()) {
            lines.add("  (camel validate yaml <file> for the full report; the loader's error follows)");
        }
        return lines;
    }

    /** The YAML route files among the files of a run: local files ending in .yaml or .yml. */
    public static List<Path> yamlFiles(List<String> files) {
        List<Path> answer = new ArrayList<>();
        if (files == null) {
            return answer;
        }
        for (String f : files) {
            if (f == null) {
                continue;
            }
            if (f.startsWith("file:")) {
                f = f.substring(5);
            }
            if (ResourceHelper.hasScheme(f) || f.startsWith("github:")) {
                continue; // github:, https:, classpath: (the check Run makes; a Windows drive letter is not a scheme)
            }
            String lower = f.toLowerCase();
            Path path = Path.of(f);
            if ((lower.endsWith(".yaml") || lower.endsWith(".yml")) && Files.isRegularFile(path) && !answer.contains(path)) {
                answer.add(path);
            }
        }
        return answer;
    }

    private static CamelCatalog catalog() {
        CamelCatalog answer = catalog;
        if (answer == null) {
            synchronized (YamlLoadFailureReport.class) {
                if (catalog == null) {
                    catalog = new DefaultCamelCatalog();
                }
                answer = catalog;
            }
        }
        return answer;
    }

    /**
     * Prints the report for a route file whose reload failed in dev mode: the file watcher emits the reload failure
     * event with the file name as the action.
     */
    public static final class ReloadFailureNotifier extends SimpleEventNotifierSupport {

        private final Consumer<String> printer;

        /** Logs the report, as {@link #logIfYamlLoadFailure(Throwable, List)} does. */
        public ReloadFailureNotifier() {
            this(null);
        }

        public ReloadFailureNotifier(Consumer<String> printer) {
            this.printer = printer;
            setIgnoreExchangeEvents(true);
            setIgnoreServiceEvents(true);
            // EventHelper.notifyContextReloadFailure skips notifiers where isIgnoreRouteEvents() is true;
            // leave ignoreRouteEvents at its default (false) so CamelContextReloadFailureEvent reaches us.
        }

        @Override
        public boolean isEnabled(CamelEvent event) {
            return event instanceof CamelEvent.CamelContextReloadFailureEvent;
        }

        @Override
        public void notify(CamelEvent event) {
            // the file watcher emits the event with the file name as the action (the API's getSource() is the context)
            if (event instanceof CamelContextReloadFailureEvent failure
                    && failure.getAction() instanceof String name && isYamlLoadFailure(failure.getCause())) {
                List<String> lines = report(yamlFiles(List.of(name)));
                if (lines.isEmpty()) {
                    return;
                }
                if (printer != null) {
                    lines.forEach(printer);
                } else {
                    LOG.error(String.join(System.lineSeparator(), lines));
                }
            }
        }
    }
}
