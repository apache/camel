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
package org.apache.camel.builder;

import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.processor.CamelLogger;
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.spi.RouteContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the {@link Logger} as an error handler, will log at <tt>ERROR</tt> level by default.
 *
 * @version 
 */
public class LoggingErrorHandlerBuilder extends ErrorHandlerBuilderSupport {
    private Logger log = LoggerFactory.getLogger(Logger.class);
    private LoggingLevel level = LoggingLevel.ERROR;

    public LoggingErrorHandlerBuilder() {
    }

    public LoggingErrorHandlerBuilder(final Logger log) {
        this.log = log;
    }

    public LoggingErrorHandlerBuilder(final Logger log, final LoggingLevel level) {
        this.log = log;
        this.level = level;
    }

    public boolean supportTransacted() {
        return false;
    }

    public Processor createErrorHandler(final RouteContext routeContext, final Processor processor) {
        CamelLogger logger = new CamelLogger(log, level);

        LoggingErrorHandler handler = new LoggingErrorHandler(routeContext.getCamelContext(), processor, logger, getExceptionPolicyStrategy());
        configure(handler);
        return handler;
    }

    public LoggingLevel getLevel() {
        return level;
    }

    public void setLevel(final LoggingLevel level) {
        this.level = level;
    }

    public Logger getLog() {
        return log;
    }

    public void setLog(final Logger log) {
        this.log = log;
    }

    public LoggingErrorHandlerBuilder level(final LoggingLevel level) {
        this.level = level;
        return this;
    }

    public LoggingErrorHandlerBuilder log(final Logger log) {
        this.log = log;
        return this;
    }

}
