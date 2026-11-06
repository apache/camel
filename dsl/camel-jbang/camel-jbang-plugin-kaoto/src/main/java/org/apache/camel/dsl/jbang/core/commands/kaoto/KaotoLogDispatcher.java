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
package org.apache.camel.dsl.jbang.core.commands.kaoto;

import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * A single Log4j2 appender that dispatches formatted log events to registered listeners. Installs itself lazily into
 * the logger context when the first listener registers and re-installs automatically after any Log4j reconfiguration
 * (e.g. when {@code RuntimeUtil.configureLog()} replaces the configuration on the first Camel command).
 */
final class KaotoLogDispatcher extends AbstractAppender {

    private static final AtomicInteger INSTANCE_COUNTER = new AtomicInteger();

    private final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean initialized;
    private volatile boolean installing;
    private PropertyChangeListener configChangeListener;

    KaotoLogDispatcher() {
        super("kaoto-log-" + INSTANCE_COUNTER.incrementAndGet(), null,
              PatternLayout.newBuilder()
                      .withPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%t] %logger{36} - %msg%n")
                      .build(),
              true, null);
    }

    private void ensureInstalled() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    configChangeListener = evt -> {
                        if (!listeners.isEmpty()) {
                            installIntoCurrentConfig();
                        }
                    };
                    LoggerContext context = (LoggerContext) LogManager.getContext(false);
                    context.addPropertyChangeListener(configChangeListener);
                    initialized = true;
                }
            }
        }
        installIntoCurrentConfig();
    }

    private void installIntoCurrentConfig() {
        if (installing) {
            return;
        }
        installing = true;
        try {
            if (!isStarted()) {
                start();
            }
            LoggerContext context = (LoggerContext) LogManager.getContext(false);
            Configuration config = context.getConfiguration();
            config.addAppender(this);
            LoggerConfig rootLoggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
            rootLoggerConfig.addAppender(this, Level.ALL, null);
            context.updateLoggers();
        } finally {
            installing = false;
        }
    }

    void uninstall() {
        if (!initialized) {
            return;
        }
        initialized = false;
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        if (configChangeListener != null) {
            context.removePropertyChangeListener(configChangeListener);
        }
        Configuration config = context.getConfiguration();
        LoggerConfig rootLoggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        rootLoggerConfig.removeAppender(getName());
        context.updateLoggers();
        stop();
    }

    void addListener(Consumer<String> listener) {
        ensureInstalled();
        listeners.add(listener);
    }

    void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    @Override
    public void append(LogEvent event) {
        if (listeners.isEmpty()) {
            return;
        }
        String formatted = getLayout().toSerializable(event).toString();
        for (Consumer<String> listener : listeners) {
            listener.accept(formatted);
        }
    }
}
