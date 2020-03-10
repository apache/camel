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
package org.apache.camel.reifier;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.LogProcessor;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.support.processor.DefaultMaskingFormatter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LogReifier extends ProcessorReifier<LogDefinition> {

    public LogReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (LogDefinition)definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        StringHelper.notEmpty(definition.getMessage(), "message", this);
        String msg = parseString(definition.getMessage());

        // use simple language for the message string to give it more power
        Expression exp = camelContext.resolveLanguage("simple").createExpression(msg);

        // get logger explicitly set in the definition
        Logger logger = definition.getLogger();

        // get logger which may be set in XML definition
        if (logger == null && ObjectHelper.isNotEmpty(definition.getLoggerRef())) {
            logger = mandatoryLookup(definition.getLoggerRef(), Logger.class);
        }

        if (logger == null) {
            // first - try to lookup single instance in the registry, just like
            // LogComponent
            Map<String, Logger> availableLoggers = findByTypeWithName(Logger.class);
            if (availableLoggers.size() == 1) {
                logger = availableLoggers.values().iterator().next();
                log.debug("Using custom Logger: {}", logger);
            } else if (availableLoggers.size() > 1) {
                // we should log about this somewhere...
                log.debug("More than one {} instance found in the registry. Falling back to create logger by name.", Logger.class.getName());
            }
        }

        if (logger == null) {
            String name = parseString(definition.getLogName());
            if (name == null) {
                name = camelContext.getGlobalOption(Exchange.LOG_EIP_NAME);
                if (name != null) {
                    log.debug("Using logName from CamelContext properties: {}", name);
                }
            }
            if (name == null) {
                name = route.getRouteId();
                log.debug("LogName is not configured, using route id as logName: {}", name);
            }
            logger = LoggerFactory.getLogger(name);
        }

        // should be INFO by default
        LoggingLevel level = definition.getLoggingLevel() != null ? parse(LoggingLevel.class, definition.getLoggingLevel()) : LoggingLevel.INFO;
        CamelLogger camelLogger = new CamelLogger(logger, level, definition.getMarker());

        return new LogProcessor(exp, camelLogger, getMaskingFormatter(), camelContext.adapt(ExtendedCamelContext.class).getLogListeners());
    }

    private MaskingFormatter getMaskingFormatter() {
        if (route.isLogMask()) {
            MaskingFormatter formatter = lookup(MaskingFormatter.CUSTOM_LOG_MASK_REF, MaskingFormatter.class);
            if (formatter == null) {
                formatter = new DefaultMaskingFormatter();
            }
            return formatter;
        }
        return null;
    }

}
