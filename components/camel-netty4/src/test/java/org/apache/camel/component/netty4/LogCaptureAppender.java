/**
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
package org.apache.camel.component.netty4;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 */
@Plugin(name = "LogCaptureAppender", category = "Core", elementType = "appender", printObject = true)
public class LogCaptureAppender extends AbstractAppender {
    private static final Deque<LogEvent> LOG_EVENTS = new ArrayDeque<>();

    public LogCaptureAppender(String name, Filter filter, Layout<? extends Serializable> layout) {
        super(name, filter, layout);
    }

    public LogCaptureAppender(String name, Filter filter, Layout<? extends Serializable> layout, boolean ignoreExceptions) {
        super(name, filter, layout, ignoreExceptions);
    }

    @PluginFactory
    public static LogCaptureAppender createAppender(@PluginAttribute("name") final String name,
                                                    @PluginElement("Filter") final Filter filter) {
        return new LogCaptureAppender(name, filter, null);
    }

    @Override
    public void append(LogEvent logEvent) {
        LOG_EVENTS.add(logEvent);
    }

    public static void reset() {
        LOG_EVENTS.clear();
    }

    public static Collection<LogEvent> getEvents() {
        return LOG_EVENTS;
    }
}
