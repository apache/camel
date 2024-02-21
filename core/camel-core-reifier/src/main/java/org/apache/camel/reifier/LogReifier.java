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

import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.LogDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.LogProcessor;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.support.LanguageSupport;
import org.apache.camel.support.processor.DefaultMaskingFormatter;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.support.LoggerHelper.getLineNumberLoggerName;

public class LogReifier extends ProcessorReifier<LogDefinition> {

    private static final Logger LOG = LoggerFactory.getLogger(LogReifier.class);

    public LogReifier(Route route, ProcessorDefinition<?> definition) {
        super(route, (LogDefinition) definition);
    }

    @Override
    public Processor createProcessor() throws Exception {
        StringHelper.notEmpty(definition.getMessage(), "message", this);
        String msg = parseString(definition.getMessage());

        // use simple language for the message string to give it more power
        Expression exp = null;
        if (LanguageSupport.hasSimpleFunction(msg)) {
            exp = camelContext.resolveLanguage("simple").createExpression(msg);
        }

        // get logger explicitly set in the definition
        Logger logger = definition.getLoggerBean();

        // get logger which may be set in XML definition
        if (logger == null && ObjectHelper.isNotEmpty(definition.getLogger())) {
            logger = mandatoryLookup(definition.getLogger(), Logger.class);
        }

        if (logger == null) {
            // first - try to lookup single instance in the registry, just like LogComponent
            logger = findSingleByType(Logger.class);
        }

        if (logger == null) {
            String name = parseString(definition.getLogName());
            if (name == null) {
                name = camelContext.getGlobalOption(Exchange.LOG_EIP_NAME);
                if (name != null) {
                    LOG.debug("Using logName from CamelContext properties: {}", name);
                }
            }
            if (name == null) {
                if (camelContext.isSourceLocationEnabled()) {
                    name = getLineNumberLoggerName(definition);
                    if (name != null) {
                        LOG.debug("LogName is not configured, using source location as logName: {}", name);
                    }
                }
                if (name == null) {
                    name = route.getRouteId();
                    LOG.debug("LogName is not configured, using route id as logName: {}", name);
                }
            }
            logger = LoggerFactory.getLogger(name);
        }

        // should be INFO by default
        LoggingLevel level = definition.getLoggingLevel() != null
                ? parse(LoggingLevel.class, definition.getLoggingLevel()) : LoggingLevel.INFO;
        CamelLogger camelLogger = new CamelLogger(logger, level, definition.getMarker());

        if (exp != null) {
            // dynamic log message via simple expression
            return new LogProcessor(
                    exp, camelLogger, getMaskingFormatter(), camelContext.getCamelContextExtension().getLogListeners());
        } else {
            // static log message via string message
            return new LogProcessor(
                    msg, camelLogger, getMaskingFormatter(), camelContext.getCamelContextExtension().getLogListeners());
        }
    }

    private MaskingFormatter getMaskingFormatter() {
        if (route.isLogMask()) {
            MaskingFormatter formatter = lookupByNameAndType(MaskingFormatter.CUSTOM_LOG_MASK_REF, MaskingFormatter.class);
            if (formatter == null) {
                formatter = new DefaultMaskingFormatter();
            }
            return formatter;
        }
        return null;
    }

}
