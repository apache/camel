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
package org.apache.camel.dsl.jbang.core.commands;

import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.dsl.jbang.core.commands.ai.YamlLoadFailureReport;
import org.apache.camel.dsl.jbang.export.ExportContext;
import org.apache.camel.dsl.jbang.export.ExportRequest;
import org.apache.camel.dsl.jbang.export.ProjectExporter;
import org.apache.camel.dsl.jbang.export.common.ExportHelper;
import org.apache.camel.dsl.jbang.export.common.RuntimeType;
import org.apache.camel.dsl.jbang.export.common.StringPrinter;
import org.apache.camel.spi.CamelContextTracker;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/**
 * Exports through the Java API with the default dependency resolver (the silent run), as an application that embeds the
 * export does: the export keeps to its work directory, leaves the logging and the system properties of the JVM as they
 * are, and releases its silent run.
 */
class ExportApiTest {

    @TempDir
    Path dir;

    private Path route;

    @BeforeEach
    void copyRoute() throws Exception {
        route = dir.resolve("hello.camel.yaml");
        Files.copy(Path.of("src/test/resources/hello.camel.yaml"), route);
    }

    @Test
    void exportsWithoutSideEffects() throws Exception {
        ExportRequest request = request(0);

        Configuration logging = LoggerContext.getContext(false).getConfiguration();
        Map<Object, Object> properties = new HashMap<>(System.getProperties());

        StringPrinter printer = new StringPrinter();
        int exit = new ProjectExporter(new ExportContext().withPrinter(printer)).export(request);

        assertThat(exit).as(printer.getOutput()).isZero();
        assertThat(Files.readString(dir.resolve("export0/pom.xml"))).contains("<artifactId>camel-timer</artifactId>");
        // the silent run writes its settings to the work directory of the request (the export tests in
        // camel-jbang-export check that the current directory is left alone, which the forks of this module share)
        assertThat(request.workDir.resolve(ExportHelper.RUN_SETTINGS_FILE)).content()
                .contains("camel.main.routesIncludePattern=", "hello.camel.yaml");

        assertThat(LoggerContext.getContext(false).getConfiguration()).isSameAs(logging);
        assertThat(new HashMap<>(System.getProperties())).isEqualTo(properties);
    }

    @Test
    void repeatedExportsReleaseTheirSilentRuns() throws Exception {
        // the first export starts what stays for the JVM, such as the shared pools of the Maven resolver
        export(0);
        Set<Thread> threads = Thread.getAllStackTraces().keySet();

        List<WeakReference<CamelContext>> contexts = new CopyOnWriteArrayList<>();
        try (CamelContextTracker tracker = new CamelContextTracker() {
            @Override
            public void contextCreated(CamelContext camelContext) {
                contexts.add(new WeakReference<>(camelContext));
            }
        }) {
            tracker.open();
            for (int i = 1; i <= 20; i++) {
                export(i);
            }
        }

        assertThat(contexts).hasSizeGreaterThanOrEqualTo(20);
        // nothing holds on to the CamelContext of a silent run once its export is done
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            System.gc();
            assertThat(contexts).allSatisfy(ref -> assertThat(ref.get()).isNull());
        });
        // except the thread of Awaitility itself, which can still be stopping after the await above
        assertThat(Thread.getAllStackTraces().keySet())
                .filteredOn(t -> !t.getName().startsWith("awaitility"))
                .allSatisfy(t -> assertThat(threads).contains(t));
    }

    private ExportRequest request(int i) {
        ExportRequest request = new ExportRequest();
        request.files.add(route.toString());
        request.runtime = RuntimeType.main;
        request.exportDir = dir.resolve("export" + i).toString();
        request.workDir = dir.resolve("work" + i);
        request.mavenWrapper = false;
        request.quiet = true;
        return request;
    }

    @Test
    void aYamlRouteThatDoesNotLoadIsReportedAsCamelRunDoes() throws Exception {
        Files.writeString(route, """
                - route:
                    from:
                      uri: timer:tick
                      steps:
                        - pollEnrich:
                            uri: file:./order.json
                """);
        List<String> logged = new CopyOnWriteArrayList<>();
        Appender appender = new AbstractAppender("report", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                logged.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        Logger logger = (Logger) LogManager.getLogger(YamlLoadFailureReport.class);
        logger.addAppender(appender);
        try {
            ExportContext context = CliExportContext.create(new CamelJBangMain(), new StringPrinter(), true);
            assertThatThrownBy(() -> new ProjectExporter(context).export(request(0))).isNotNull();
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }

        // the report of camel validate yaml, as camel run logs it (CAMEL-24851)
        assertThat(logged).singleElement().asString()
                .startsWith("The route file did not load. camel validate yaml says what to write:")
                .contains("hello.camel.yaml", "pollEnrich");
    }

    private void export(int i) throws Exception {
        StringPrinter printer = new StringPrinter();
        assertThat(new ProjectExporter(new ExportContext().withPrinter(printer)).export(request(i)))
                .as(printer.getOutput()).isZero();
    }
}
