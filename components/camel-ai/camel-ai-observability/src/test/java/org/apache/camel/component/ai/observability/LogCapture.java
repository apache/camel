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
package org.apache.camel.component.ai.observability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

final class LogCapture implements AutoCloseable {

    private final Logger logger;
    private final AbstractAppender appender;
    private final List<String> infoMessages = new CopyOnWriteArrayList<>();
    private final List<String> warnMessages = new CopyOnWriteArrayList<>();

    private LogCapture(Class<?> type) {
        appender = new AbstractAppender("GenAiObservabilityCapture", null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.INFO) {
                    infoMessages.add(event.getMessage().getFormattedMessage());
                } else if (event.getLevel() == Level.WARN) {
                    warnMessages.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        appender.start();
        logger = (Logger) LogManager.getLogger(type);
        logger.addAppender(appender);
    }

    static LogCapture attach(Class<?> type) {
        return new LogCapture(type);
    }

    List<String> infoMessages() {
        return infoMessages;
    }

    List<String> warnMessages() {
        return warnMessages;
    }

    @Override
    public void close() {
        logger.removeAppender(appender);
        appender.stop();
    }
}
