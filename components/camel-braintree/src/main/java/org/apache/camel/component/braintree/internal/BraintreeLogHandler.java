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
package org.apache.camel.component.braintree.internal;

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Simplified version of org.slf4j.bridge.SLF4JBridgeHandler
 */
public final class BraintreeLogHandler extends Handler {
    public static final Handler INSTANCE = new BraintreeLogHandler();
    public static final Level DEFAULT_LOGGER_VERSION = Level.WARNING;
    public static final String DEFAULT_LOGGER_NAME = "org.apache.camel.component.braintree.camel-braintree";

    private static final int TRACE_LEVEL_THRESHOLD = Level.FINEST.intValue();
    private static final int DEBUG_LEVEL_THRESHOLD = Level.FINE.intValue();
    private static final int INFO_LEVEL_THRESHOLD = Level.INFO.intValue();
    private static final int WARN_LEVEL_THRESHOLD = Level.WARNING.intValue();

    @Override
    public void flush() {
        // no-op
    }

    @Override
    public void close() throws SecurityException {
        // no-op
    }

    @Override
    public void publish(LogRecord record) {
        if (record != null) {
            final Logger logger = getLogger(record);
            final String message = formatMessage(record);

            int level = record.getLevel().intValue();
            if (level <= TRACE_LEVEL_THRESHOLD) {
                logger.trace(message, record.getThrown());
            } else if (level <= DEBUG_LEVEL_THRESHOLD) {
                logger.debug(message, record.getThrown());
            } else if (level <= INFO_LEVEL_THRESHOLD) {
                logger.info(message, record.getThrown());
            } else if (level <= WARN_LEVEL_THRESHOLD) {
                logger.warn(message, record.getThrown());
            } else {
                logger.error(message, record.getThrown());
            }
        }
    }

    private Logger getLogger(LogRecord record) {
        String name = record.getLoggerName();
        if (name == null) {
            name = DEFAULT_LOGGER_NAME;
        }
        return LoggerFactory.getLogger(name);
    }

    private String formatMessage(LogRecord record) {
        String message = record.getMessage();
        if (message != null) {

            ResourceBundle bundle = record.getResourceBundle();
            if (bundle != null) {
                try {
                    message = bundle.getString(message);
                } catch (MissingResourceException e) {
                }
            }
            Object[] params = record.getParameters();
            // avoid formatting when there are no or 0 parameters. see also
            // http://jira.qos.ch/browse/SLF4J-203
            if (params != null && params.length > 0) {
                try {
                    message = MessageFormat.format(message, params);
                } catch (IllegalArgumentException e) {
                    // default to the same behavior as in java.util.logging.Formatter.formatMessage(LogRecord)
                    // see also http://jira.qos.ch/browse/SLF4J-337
                    return message;
                }
            }
        } else {
            message = "";
        }

        return message;
    }
}
