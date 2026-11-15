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
package org.apache.camel.component.quartz;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.Isolated;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Quartz itself reads the file named by the org.quartz.properties system property, or a quartz.properties at the root
 * of the classpath, while the quartz component reads only org/quartz/quartz.properties unless an option says otherwise.
 */
@Isolated("sets the org.quartz.properties system property")
class QuartzIgnoredPropertiesFileWarningTest extends CamelTestSupport {

    // the thread count of org/quartz/quartz.properties in the Quartz JAR
    private static final int DEFAULT_THREAD_COUNT = 10;

    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender
            = new AbstractAppender("QuartzIgnoredPropertiesFile-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    if (event.getLevel() == Level.WARN) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    @TempDir
    Path dir;

    private URLClassLoader classLoader;

    @Override
    public boolean isUseRouteBuilder() {
        // each test configures the component before it starts the context
        return false;
    }

    @BeforeEach
    void captureWarnings() {
        appender.start();
        componentLogger().addAppender(appender);
        // addAppender creates a non-additive logger config, which would hide this logger from the root appenders
        componentLogger().setAdditive(true);
    }

    @AfterEach
    void stopCapture() {
        componentLogger().removeAppender(appender);
        appender.stop();
        System.clearProperty(StdSchedulerFactory.PROPERTIES_FILE);
    }

    @Override
    public void doPostTearDown() throws Exception {
        if (classLoader != null) {
            classLoader.close();
        }
    }

    @Test
    void warnsWhenQuartzPropertiesAtClasspathRootIsNotUsed() throws Exception {
        addQuartzPropertiesToClasspathRoot();

        QuartzComponent quartz = startQuartz(null);

        assertEquals(DEFAULT_THREAD_COUNT, threadPoolSize(quartz));
        assertEquals(1, warningsContaining("propertiesFile=classpath:quartz.properties"), warnings::toString);
    }

    @Test
    void warnsWhenFileOfSystemPropertyIsNotUsed() throws Exception {
        Path file = writeQuartzProperties(dir.resolve("my-quartz.properties"));
        System.setProperty(StdSchedulerFactory.PROPERTIES_FILE, file.toString());

        QuartzComponent quartz = startQuartz(null);

        assertEquals(DEFAULT_THREAD_COUNT, threadPoolSize(quartz));
        assertEquals(1, warningsContaining(file.toString()), warnings::toString);
    }

    @Test
    void usesQuartzPropertiesAtClasspathRootWithPropertiesFile() throws Exception {
        addQuartzPropertiesToClasspathRoot();

        QuartzComponent quartz = startQuartz("classpath:quartz.properties");

        assertEquals(3, threadPoolSize(quartz));
        assertEquals(0, warningsContaining("quartz.properties"), warnings::toString);
    }

    @Test
    void noWarningWithoutQuartzPropertiesFile() throws Exception {
        QuartzComponent quartz = startQuartz(null);

        assertEquals(DEFAULT_THREAD_COUNT, threadPoolSize(quartz));
        assertEquals(0, warningsContaining("quartz.properties"), warnings::toString);
    }

    private QuartzComponent startQuartz(String propertiesFile) {
        QuartzComponent quartz = context.getComponent("quartz", QuartzComponent.class);
        quartz.setPropertiesFile(propertiesFile);
        context.start();
        return quartz;
    }

    private void addQuartzPropertiesToClasspathRoot() throws Exception {
        writeQuartzProperties(dir.resolve("quartz.properties"));
        classLoader = new URLClassLoader(new URL[] { dir.toUri().toURL() }, getClass().getClassLoader());
        context.setApplicationContextClassLoader(classLoader);
    }

    private static Path writeQuartzProperties(Path file) throws Exception {
        return Files.writeString(file, """
                org.quartz.scheduler.instanceName=MyScheduler
                org.quartz.threadPool.class=org.quartz.simpl.SimpleThreadPool
                org.quartz.threadPool.threadCount=3
                org.quartz.jobStore.class=org.quartz.simpl.RAMJobStore
                """);
    }

    private static int threadPoolSize(QuartzComponent quartz) throws SchedulerException {
        return quartz.getScheduler().getMetaData().getThreadPoolSize();
    }

    private long warningsContaining(String text) {
        return warnings.stream().filter(message -> message.contains(text)).count();
    }

    private static Logger componentLogger() {
        return (Logger) LogManager.getLogger(QuartzComponent.class);
    }
}
