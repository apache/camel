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
package org.apache.camel.component.jgroups;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import static org.awaitility.Awaitility.await;

/**
 * Captures the WARN messages of the logger of a class, while it is open.
 */
public final class WarnCapture implements AutoCloseable {

    private final Logger logger;
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AbstractAppender appender;

    public WarnCapture(Class<?> type) {
        logger = (Logger) LogManager.getLogger(type);
        appender = new AbstractAppender("WarnCapture-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                if (event.getLevel() == Level.WARN) {
                    warnings.add(event.getMessage().getFormattedMessage());
                }
            }
        };
        // the test classes may run in parallel: while another one is still starting log4j, the logger has the default
        // configuration, which log4j replaces once it has read log4j2.properties, and an appender attached to it is
        // stopped and lost, so attach it once log4j has started
        await().atMost(30, TimeUnit.SECONDS).until(logger.getContext()::isStarted);
        appender.start();
        logger.addAppender(appender);
        // addAppender creates a non-additive logger config, which would hide this logger from the root appenders
        logger.setAdditive(true);
    }

    /**
     * The number of WARN messages that contain all the texts.
     */
    public long count(String... texts) {
        return warnings.stream().filter(message -> Arrays.stream(texts).allMatch(message::contains)).count();
    }

    public List<String> warnings() {
        return warnings;
    }

    @Override
    public void close() {
        logger.removeAppender(appender);
        appender.stop();
    }
}
