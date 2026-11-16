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
package org.apache.camel.component.file.remote;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Captures the WARN messages of the remote file consumer, between {@link #attach()} and {@link #detach()}.
 */
public class RemoteFileConsumerWarnCapture extends AbstractAppender {

    private final List<String> messages = new CopyOnWriteArrayList<>();

    public RemoteFileConsumerWarnCapture() {
        super("RemoteFileConsumerWarnCapture", null, null, true, Property.EMPTY_ARRAY);
    }

    public void attach() {
        start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().addAppender(this, Level.WARN, null);
        ctx.updateLoggers();
    }

    public void detach() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().getRootLogger().removeAppender(getName());
        ctx.updateLoggers();
        stop();
    }

    /**
     * The WARN messages logged so far.
     */
    public List<String> messages() {
        return messages;
    }

    @Override
    public void append(LogEvent event) {
        if (RemoteFileConsumer.class.getName().equals(event.getLoggerName())) {
            messages.add(event.getMessage().getFormattedMessage());
        }
    }
}
