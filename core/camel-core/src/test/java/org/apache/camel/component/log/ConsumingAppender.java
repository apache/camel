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
package org.apache.camel.component.log;

import java.util.function.Consumer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

public class ConsumingAppender extends AbstractAppender {
    private final Consumer<LogEvent> consumer;

    public ConsumingAppender(String name, Consumer<LogEvent> consumer) {
        this(name, PatternLayout.SIMPLE_CONVERSION_PATTERN, consumer);
    }

    public ConsumingAppender(String name, String pattern, Consumer<LogEvent> consumer) {
        super(name, null, PatternLayout.newBuilder().withPattern(pattern).build());
        this.consumer = consumer;
    }

    @Override
    public void append(LogEvent event) {
        this.consumer.accept(event);
    }

    // *******************
    // Helpers
    // *******************

    public static Appender newAppender(String loggerName, String appenderName, Level level, Consumer<LogEvent> consumer) {
        return newAppender(loggerName, appenderName, PatternLayout.SIMPLE_CONVERSION_PATTERN, level, consumer);
    }

    public static Appender newAppender(String loggerName, String appenderName, String patter, Level level, Consumer<LogEvent> consumer) {
        final LoggerContext ctx = (LoggerContext)LogManager.getContext(false);
        final Configuration config = ctx.getConfiguration();

        config.removeLogger(loggerName);

        ConsumingAppender appender = new ConsumingAppender(appenderName, patter, consumer);
        appender.start();

        LoggerConfig loggerConfig = LoggerConfig.createLogger(true, level, loggerName, "true", new AppenderRef[] {AppenderRef.createAppenderRef(appenderName, null, null)}, null,
                                                              config, null);

        loggerConfig.addAppender(appender, null, null);
        config.addLogger(loggerName, loggerConfig);

        ctx.updateLoggers();

        return appender;
    }
}
