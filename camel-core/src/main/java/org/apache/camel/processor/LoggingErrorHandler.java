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
package org.apache.camel.processor;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An {@link ErrorHandler} which uses commons-logging to dump the error
 * 
 * @version $Revision$
 */
public class LoggingErrorHandler extends ErrorHandlerSupport {
    private Processor output;
    private Log log;
    private LoggingLevel level;

    public LoggingErrorHandler(Processor output) {
        this(output, LogFactory.getLog(LoggingErrorHandler.class), LoggingLevel.INFO);
    }

    public LoggingErrorHandler(Processor output, Log log, LoggingLevel level) {
        this.output = output;
        this.log = log;
        this.level = level;
    }

    @Override
    public String toString() {
        return "LoggingErrorHandler[" + output + "]";
    }

    public void process(Exchange exchange) throws Exception {
        try {
            output.process(exchange);
        } catch (Throwable e) {
            if (!customProcessorForException(exchange, e)) {
                logError(exchange, e);
            }
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    /**
     * Returns the output processor
     */
    public Processor getOutput() {
        return output;
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

    // Implementation methods
    // -------------------------------------------------------------------------
    protected void logError(Exchange exchange, Throwable e) {
        switch (level) {
        case DEBUG:
            if (log.isDebugEnabled()) {
                log.debug(logMessage(exchange, e), e);
            }
            break;
        case ERROR:
            if (log.isErrorEnabled()) {
                log.error(logMessage(exchange, e), e);
            }
            break;
        case FATAL:
            if (log.isFatalEnabled()) {
                log.fatal(logMessage(exchange, e), e);
            }
            break;
        case INFO:
            if (log.isInfoEnabled()) {
                log.debug(logMessage(exchange, e), e);
            }
            break;
        case TRACE:
            if (log.isTraceEnabled()) {
                log.trace(logMessage(exchange, e), e);
            }
            break;
        case WARN:
            if (log.isWarnEnabled()) {
                log.warn(logMessage(exchange, e), e);
            }
            break;
        default:
            log.error("Unknown level: " + level + " when trying to log exchange: " + logMessage(exchange, e),
                      e);
        }
    }

    protected Object logMessage(Exchange exchange, Throwable e) {
        return e + " while processing exchange: " + exchange;
    }

    protected void doStart() throws Exception {
        ServiceHelper.startServices(output);
    }

    protected void doStop() throws Exception {
        ServiceHelper.stopServices(output);
    }
}
