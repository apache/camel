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
package org.apache.camel.component.file;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.log.ConsumingAppender;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The directory of a file endpoint is a directory of the file system: file:classpath:data is a directory named
 * classpath:data, which the consumer creates. The endpoint is still created, and the component logs a WARN that points
 * to the language component.
 */
// isolated: the test captures the log of FileComponent
@Isolated
class FileComponentClasspathDirectoryTest extends ContextTestSupport {

    private static final String LOGGER = FileComponent.class.getName();

    private final List<String> warnings = new CopyOnWriteArrayList<>();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @BeforeEach
    void captureWarnings() {
        ConsumingAppender.newAppender(LOGGER, getClass().getSimpleName(), Level.WARN, event -> {
            if (event.getLevel().isMoreSpecificThan(Level.WARN)) {
                warnings.add(event.getMessage().getFormattedMessage());
            }
        });
    }

    @AfterEach
    void removeAppender() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().removeLogger(LOGGER);
        ctx.updateLoggers();
    }

    @Test
    void classpathDirectoryLogsWarn() {
        FileEndpoint endpoint = assertInstanceOf(FileEndpoint.class, context.getEndpoint("file:classpath:data"));

        // a directory of the file system, as before
        assertEquals("classpath:data", endpoint.getConfiguration().getDirectory());
        assertEquals(List.of("The directory classpath:data of file://classpath:data is a directory of the file system, not"
                             + " of the classpath: the file component does not read classpath resources. To read a"
                             + " classpath resource use the language component, such as"
                             + " language:constant:resource:classpath:data/<file name>"),
                warnings);
    }

    @Test
    void resourceDirectoryLogsWarn() {
        context.getEndpoint("file:resource:classpath:?fileName=log4j2.properties");

        assertEquals(1, warnings.size(), "warnings: " + warnings);
        assertTrue(warnings.get(0).startsWith("The directory resource:classpath: of file://resource:classpath:"),
                warnings.get(0));
        assertTrue(warnings.get(0).endsWith("such as language:constant:resource:classpath:<file name>"), warnings.get(0));
    }

    @Test
    void fileSystemDirectoryDoesNotWarn() {
        context.getEndpoint(fileUri("data"));

        assertTrue(warnings.isEmpty(), "warnings: " + warnings);
    }
}
