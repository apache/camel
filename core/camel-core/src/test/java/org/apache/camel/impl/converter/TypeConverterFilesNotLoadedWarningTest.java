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
package org.apache.camel.impl.converter;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.TypeConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.converter.custom.MyBean;
import org.apache.camel.support.PluginHelper;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Camel logs a WARN on startup when type converters on the classpath are not loaded: a
 * META-INF/services/org/apache/camel/TypeConverter file while loadTypeConverters is off, or no
 * META-INF/services/org/apache/camel/TypeConverterLoader file that lists the core converters of camel-base (a fat jar
 * that kept the file of another Camel JAR).
 * <p>
 * The camel-core test classpath has a META-INF/services/org/apache/camel/TypeConverter file that lists the package of
 * {@link org.apache.camel.processor.converter.custom.MyConverter}, which converts "a:b" to a {@link MyBean}.
 * <p>
 * Isolated because other test classes start contexts that log the same WARN.
 */
@Isolated
public class TypeConverterFilesNotLoadedWarningTest {

    private static final String LEGACY_FILE = AnnotationTypeConverterLoader.META_INF_SERVICES;
    private static final String LOADER_FILE = BaseTypeConverterRegistry.META_INF_SERVICES_TYPE_CONVERTER_LOADER;
    private static final String UBER_LOADER_FILE = BaseTypeConverterRegistry.META_INF_SERVICES_UBER_TYPE_CONVERTER_LOADER;
    private static final String CORE_LOADER = "org.apache.camel.converter.CamelBaseBulkConverterLoader";
    private static final String SUPPORT_LOADER = "org.apache.camel.converter.stream.CamelSupportBulkConverterLoader";

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    @TempDir
    Path dir;

    @BeforeEach
    public void captureWarnings() {
        appender = new AbstractAppender(
                "type-converter-warnings-" + UUID.randomUUID(), null, null, true,
                Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(Level.WARN)
                        && event.getLoggerName().startsWith("org.apache.camel.impl.converter")) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        logging.updateLoggers();
    }

    @AfterEach
    public void stopCapture() {
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().removeAppender(appender.getName());
        logging.updateLoggers();
        appender.stop();
    }

    @Test
    public void typeConverterFileNotReadLogsWarning() throws Exception {
        URL url = getClass().getClassLoader().getResource(LEGACY_FILE);
        assertNotNull(url, "the test classpath should have " + LEGACY_FILE);

        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.start();

            // the behaviour is unchanged: the converters listed in the file are not loaded
            assertNull(context.getTypeConverter().convertTo(MyBean.class, "a:b"));

            List<String> legacy = warnings.stream().filter(w -> w.contains(LEGACY_FILE)).toList();
            assertEquals(1, legacy.size(), warnings.toString());
            String warning = legacy.get(0);
            assertTrue(warning.contains(url.toString()), warning);
            assertTrue(warning.contains("camel.main.loadTypeConverters=true"), warning);
            assertTrue(warning.contains("@Converter(generateLoader = true)"), warning);

            // logged once on startup, not on each conversion
            assertNull(context.getTypeConverter().convertTo(MyBean.class, "c:d"));
            assertEquals(1, warnings.stream().filter(w -> w.contains(LEGACY_FILE)).count(), warnings.toString());
        }
    }

    @Test
    public void loadTypeConvertersEnabledBeforeStartReadsFileWithoutWarning() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            // enabled after the type converter is initialized, as camel-main and Spring do
            context.setLoadTypeConverters(true);
            context.start();

            assertEquals(new MyBean("a", "b"), context.getTypeConverter().convertTo(MyBean.class, "a:b"));
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    public void basePackageScanReadsFileWithoutWarning() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            context.getCamelContextExtension().setBasePackageScan("org.apache.camel.processor.converter.custom");
            context.start();

            assertEquals(new MyBean("a", "b"), context.getTypeConverter().convertTo(MyBean.class, "a:b"));
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    public void noWarningWithoutTypeConverterFile() throws Exception {
        try (DefaultCamelContext context = contextWithLoaderFiles(Map.of())) {
            context.start();

            assertEquals(5, context.getTypeConverter().convertTo(Integer.class, "5"));
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    @Test
    public void coreLoaderMissingLogsWarning() throws Exception {
        // a fat jar that kept the TypeConverterLoader file of camel-support only
        URL loaderFile = writeFile("TypeConverterLoader", SUPPORT_LOADER);

        try (DefaultCamelContext context = contextWithLoaderFiles(Map.of(LOADER_FILE, loaderFile))) {
            context.start();

            // the behaviour is unchanged: the core converters are not loaded
            assertNull(context.getTypeConverter().convertTo(Integer.class, "5"));
            assertNull(context.getTypeConverter().convertTo(Duration.class, "0"));

            List<String> core = warnings.stream().filter(w -> w.contains(CORE_LOADER)).toList();
            assertEquals(1, core.size(), warnings.toString());
            String warning = core.get(0);
            assertTrue(warning.contains(loaderFile.toString()), warning);
            assertTrue(warning.contains("prepare-fatjar"), warning);

            // logged once on startup, not on each conversion
            assertNull(context.getTypeConverter().convertTo(Integer.class, "6"));
            assertEquals(1, warnings.stream().filter(w -> w.contains(CORE_LOADER)).count(), warnings.toString());
        }
    }

    @Test
    public void uberLoaderFileListsCoreLoaderWithoutWarning() throws Exception {
        // a fat jar built with camel:prepare-fatjar: the uber file lists all the loaders
        URL loaderFile = writeFile("TypeConverterLoader", SUPPORT_LOADER);
        URL uberFile = writeFile("UberTypeConverterLoader", CORE_LOADER + "\n" + SUPPORT_LOADER);

        try (DefaultCamelContext context
                = contextWithLoaderFiles(Map.of(LOADER_FILE, loaderFile, UBER_LOADER_FILE, uberFile))) {
            context.start();

            assertEquals(5, context.getTypeConverter().convertTo(Integer.class, "5"));
            assertEquals(Duration.ZERO, context.getTypeConverter().convertTo(Duration.class, "0"));
            assertTrue(warnings.isEmpty(), warnings.toString());
        }
    }

    private URL writeFile(String name, String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content + "\n");
        return file.toUri().toURL();
    }

    /**
     * A context whose type converter sees only the given service files, and the real TypeConverterLoader files when
     * none is given for that path.
     */
    private static DefaultCamelContext contextWithLoaderFiles(Map<String, URL> files) {
        return new DefaultCamelContext() {
            @Override
            protected TypeConverter createTypeConverter() {
                return new DefaultTypeConverter(
                        this, PluginHelper.getPackageScanClassResolver(this), getInjector(), isLoadTypeConverters(),
                        false) {
                    @Override
                    protected Collection<URL> getLoaderUrls(String basePath) throws IOException {
                        URL url = files.get(basePath);
                        if (url != null) {
                            return List.of(url);
                        }
                        if (LOADER_FILE.equals(basePath)) {
                            return super.getLoaderUrls(basePath);
                        }
                        return List.of();
                    }
                };
            }
        };
    }
}
