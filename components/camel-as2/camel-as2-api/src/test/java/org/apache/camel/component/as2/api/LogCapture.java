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
package org.apache.camel.component.as2.api;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Captures the log events of one logger, down to DEBUG, while it is open.
 */
final class LogCapture extends AbstractAppender implements AutoCloseable {

    private final List<LogEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger;
    private final Level previousLevel;

    LogCapture(Class<?> loggerClass) {
        super("LogCapture-" + loggerClass.getSimpleName(), null, null, true, Property.EMPTY_ARRAY);
        this.logger = (Logger) LogManager.getLogger(loggerClass);
        this.previousLevel = logger.getLevel();
        start();
        logger.addAppender(this);
        logger.setLevel(Level.DEBUG);
    }

    @Override
    public void append(LogEvent event) {
        events.add(event.toImmutable());
    }

    List<LogEvent> events(Level level) {
        return events.stream().filter(e -> e.getLevel() == level).toList();
    }

    boolean hasMessageContaining(String text) {
        return events.stream().anyMatch(e -> e.getMessage().getFormattedMessage().contains(text));
    }

    List<LogEvent> eventsContaining(String text) {
        return events.stream().filter(e -> e.getMessage().getFormattedMessage().contains(text)).toList();
    }

    @Override
    public void close() {
        logger.removeAppender(this);
        logger.setLevel(previousLevel);
        stop();
    }
}
