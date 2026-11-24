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
package org.apache.camel.converter.jaxp;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.xml.sax.SAXParseException;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A body that is not well-formed XML fails the conversion with the parse exception, and is not also logged at ERROR, so
 * a route that handles the exception gets no ERROR line for each such message.
 */
@Isolated
class XmlConverterParseErrorLogTest {

    private final List<String> errors = new CopyOnWriteArrayList<>();
    private AbstractAppender appender;

    @BeforeEach
    void captureErrors() {
        appender = new AbstractAppender("xml-converter-errors-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLoggerName().startsWith("org.apache.camel.converter.jaxp")) {
                    errors.add(event.getLevel() + " " + event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().addAppender(appender, Level.WARN, null);
        logging.updateLoggers();
    }

    @AfterEach
    void stopCapture() {
        LoggerContext logging = (LoggerContext) LogManager.getContext(false);
        logging.getConfiguration().getRootLogger().removeAppender(appender.getName());
        logging.updateLoggers();
        appender.stop();
    }

    @Test
    void textThatIsNotXmlThrowsWithoutLogging() {
        XmlConverter converter = new XmlConverter();

        SAXParseException e = assertThrows(SAXParseException.class, () -> converter.toDOMDocument("How long do I have?", null));

        assertEquals("Content is not allowed in prolog.", e.getMessage());
        assertEquals(List.of(), errors);
    }

    @Test
    void xmlThatIsNotWellFormedThrowsWithoutLogging() {
        XmlConverter converter = new XmlConverter();
        byte[] body = "<order><item></order>".getBytes(StandardCharsets.UTF_8);

        assertThrows(SAXParseException.class, () -> converter.toDOMDocument(body, null));

        assertEquals(List.of(), errors);
    }
}
