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
package org.apache.camel.tracing;

import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Core;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

/**
 * Log4j2 appender to catch only traced messages of the logger named <tt>org.apache.camel.Tracing</tt> collecting them
 * into List
 */
@Plugin(name = "RouteTracingListAppender", category = Core.CATEGORY_NAME, elementType = Appender.ELEMENT_TYPE)
public final class RouteTracingListAppender extends AbstractAppender {

    private static final List<String> LOG = new ArrayList<>();

    private RouteTracingListAppender(String name, Filter filter) {
        super(name, filter, null, false, null);
    }

    @Override
    public void append(LogEvent logEvent) {
        if ("org.apache.camel.Tracing".equals(logEvent.getLoggerName())) {
            String message = logEvent.getMessage().getFormattedMessage();
            if (!message.startsWith("Should")) {
                LOG.add(message);
            }
        }
    }

    @PluginFactory
    public static RouteTracingListAppender createAppender(
            @PluginAttribute("name") String name,
            @PluginElement("Filter") Filter filter) {
        return new RouteTracingListAppender(name, filter);
    }

    public static List<String> getLogs() {
        return LOG;
    }

    public static void clearLogs() {
        LOG.clear();
    }
}
