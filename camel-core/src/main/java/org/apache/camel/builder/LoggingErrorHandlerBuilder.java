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
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.processor.LoggingLevel;
import org.apache.camel.spi.RouteContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Uses the {@link Logger} as an error handler
 *
 * @version $Revision$
 */
public class LoggingErrorHandlerBuilder extends ErrorHandlerBuilderSupport {
    private Log log = LogFactory.getLog(Logger.class);
    private LoggingLevel level = LoggingLevel.INFO;

    public LoggingErrorHandlerBuilder() {
    }

    public LoggingErrorHandlerBuilder(Log log) {
        this.log = log;
    }

    public LoggingErrorHandlerBuilder(Log log, LoggingLevel level) {
        this.log = log;
        this.level = level;
    }

    public ErrorHandlerBuilder copy() {
        LoggingErrorHandlerBuilder answer = new LoggingErrorHandlerBuilder();
        answer.setLog(getLog());
        answer.setLevel(getLevel());
        return answer;
    }

    public Processor createErrorHandler(RouteContext routeContext, Processor processor) {
        LoggingErrorHandler handler = new LoggingErrorHandler(processor, log, level);
        configure(handler);
        return handler;
    }

    public LoggingLevel getLevel() {
        return level;
    }

    public void setLevel(LoggingLevel level) {
        this.level = level;
    }

    public Log getLog() {
        return log;
    }

    public void setLog(Log log) {
        this.log = log;
    }
}
