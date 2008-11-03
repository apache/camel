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

import org.apache.camel.Processor;
import org.apache.camel.model.LoggingLevel;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Uses the {@link Logger} as an error handler, will log at <tt>ERROR</tt> level by default.
 *
 * @version $Revision$
 */
public class LoggingErrorHandlerBuilder extends ErrorHandlerBuilderSupport {
    private Log log = LogFactory.getLog(Logger.class);
    private LoggingLevel level = LoggingLevel.ERROR;

    public LoggingErrorHandlerBuilder() {
    }

    public LoggingErrorHandlerBuilder(final Log log) {
        this.log = log;
    }

    public LoggingErrorHandlerBuilder(final Log log, final LoggingLevel level) {
        this.log = log;
        this.level = level;
    }

    public ErrorHandlerBuilder copy() {
        LoggingErrorHandlerBuilder answer = new LoggingErrorHandlerBuilder();
        answer.setLog(getLog());
        answer.setLevel(getLevel());
        return answer;
    }

    public Processor createErrorHandler(final RouteContext routeContext, final Processor processor) {
        LoggingErrorHandler handler = new LoggingErrorHandler(processor, log, level);
        configure(handler);
        return handler;
    }

    public LoggingLevel getLevel() {
        return level;
    }

    public void setLevel(final LoggingLevel level) {
        this.level = level;
    }

    public Log getLog() {
        return log;
    }

    public void setLog(final Log log) {
        this.log = log;
    }

    public LoggingErrorHandlerBuilder level(final LoggingLevel level) {
        this.level = level;
        return this;
    }

    public LoggingErrorHandlerBuilder log(final Log log) {
        this.log = log;
        return this;
    }

}
