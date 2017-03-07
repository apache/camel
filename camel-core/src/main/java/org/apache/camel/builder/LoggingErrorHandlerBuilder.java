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
import org.apache.camel.processor.LoggingErrorHandler;
import org.apache.camel.processor.RedeliveryPolicy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.CamelLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Uses the {@link Logger} as an error handler, will log at <tt>ERROR</tt> level by default.
 *
 * @version
 * @deprecated use dead letter channel with a log endpoint
 */
@Deprecated
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

    @Override
    public ErrorHandlerBuilder cloneBuilder() {
        LoggingErrorHandlerBuilder answer = new LoggingErrorHandlerBuilder();
        cloneBuilder(answer);
        return answer;
    }

    protected void cloneBuilder(LoggingErrorHandlerBuilder other) {
        super.cloneBuilder(other);

        other.level = level;
        other.log = log;
    }

    public Processor createErrorHandler(final RouteContext routeContext, final Processor processor) {
        CamelLogger logger = new CamelLogger(log, level);

        // configure policy to use the selected logging level, and only log exhausted
        RedeliveryPolicy policy = new RedeliveryPolicy();
        policy.setLogExhausted(true);
        policy.setRetriesExhaustedLogLevel(level);
        policy.setLogStackTrace(true);
        policy.setLogRetryAttempted(false);
        policy.setRetryAttemptedLogLevel(LoggingLevel.OFF);
        policy.setLogRetryStackTrace(false);
        policy.setLogContinued(false);
        policy.setLogHandled(false);

        LoggingErrorHandler handler = new LoggingErrorHandler(routeContext.getCamelContext(), processor, logger,
                policy, getExceptionPolicyStrategy());
        configure(routeContext, handler);
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

    public String getLogName() {
        return log != null ? log.getName() : null;
    }

    public void setLogName(String logName) {
        this.log = LoggerFactory.getLogger(logName);
    }

    public LoggingErrorHandlerBuilder level(final LoggingLevel level) {
        this.level = level;
        return this;
    }

    public LoggingErrorHandlerBuilder log(final Logger log) {
        this.log = log;
        return this;
    }

    public LoggingErrorHandlerBuilder logName(final String logName) {
        setLogName(logName);
        return this;
    }

}
