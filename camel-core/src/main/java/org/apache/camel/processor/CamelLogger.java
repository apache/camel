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

import org.apache.camel.AsyncCallback;
import org.apache.camel.AsyncProcessor;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.AsyncProcessorHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link Processor} which just logs to a {@link CamelLogger} object which can be used
 * as an exception handler instead of using a dead letter queue.
 * <p/>
 * The name <tt>CamelLogger</tt> has been chosen to avoid any name clash with log kits
 * which has a <tt>Logger</tt> class.
 *
 * @deprecated This class has been split up into org.apache.camel.util.CamelLogger and org.apache.camel.processor.CamelLogProcessor 
 */
@Deprecated
public class CamelLogger extends ServiceSupport implements AsyncProcessor {
    private Logger log;
    private LoggingLevel level;
    private ExchangeFormatter formatter;

    public CamelLogger() {
        this(LoggerFactory.getLogger(CamelLogger.class));
    }

    public CamelLogger(Logger log) {
        this(log, LoggingLevel.INFO);
    }

    public CamelLogger(Logger log, LoggingLevel level) {
        this.formatter = new CamelLogProcessor.ToStringExchangeFormatter();
        this.log = log;
        this.level = level;
    }

    public CamelLogger(String logName) {
        this(LoggerFactory.getLogger(logName));
    }

    public CamelLogger(String logName, LoggingLevel level) {
        this(LoggerFactory.getLogger(logName), level);
    }

    public CamelLogger(Logger log, ExchangeFormatter formatter) {
        this(log);
        this.formatter = formatter;
    }

    @Override
    public String toString() {
        return "Logger[" + log + "]";
    }

    public void process(Exchange exchange) throws Exception {
        AsyncProcessorHelper.process(this, exchange);
    }

    public boolean process(Exchange exchange, AsyncCallback callback) {
        switch (level) {
        case DEBUG:
            if (log.isDebugEnabled()) {
                log.debug(logMessage(exchange));
            }
            break;
        case ERROR:
            if (log.isErrorEnabled()) {
                log.error(logMessage(exchange));
            }
            break;
        case INFO:
            if (log.isInfoEnabled()) {
                log.info(logMessage(exchange));
            }
            break;
        case TRACE:
            if (log.isTraceEnabled()) {
                log.trace(logMessage(exchange));
            }
            break;
        case WARN:
            if (log.isWarnEnabled()) {
                log.warn(logMessage(exchange));
            }
            break;
        case OFF:
            break;
        default:
            log.error("Unknown level: " + level + " when trying to log exchange: " + logMessage(exchange));
        }

        callback.done(true);
        return true;
    }

    public void process(Exchange exchange, Throwable exception) {
        switch (level) {
        case DEBUG:
            if (log.isDebugEnabled()) {
                log.debug(logMessage(exchange), exception);
            }
            break;
        case ERROR:
            if (log.isErrorEnabled()) {
                log.error(logMessage(exchange), exception);
            }
            break;
        case INFO:
            if (log.isInfoEnabled()) {
                log.info(logMessage(exchange), exception);
            }
            break;
        case TRACE:
            if (log.isTraceEnabled()) {
                log.trace(logMessage(exchange), exception);
            }
            break;
        case WARN:
            if (log.isWarnEnabled()) {
                log.warn(logMessage(exchange), exception);
            }
            break;
        case OFF:
            break;
        default:
            log.error("Unknown level: " + level + " when trying to log exchange: " + logMessage(exchange));
        }
    }

    public void process(Exchange exchange, String message) {
        switch (level) {
        case DEBUG:
            if (log.isDebugEnabled()) {
                log.debug(logMessage(exchange, message));
            }
            break;
        case ERROR:
            if (log.isErrorEnabled()) {
                log.error(logMessage(exchange, message));
            }
            break;
        case INFO:
            if (log.isInfoEnabled()) {
                log.info(logMessage(exchange, message));
            }
            break;
        case TRACE:
            if (log.isTraceEnabled()) {
                log.trace(logMessage(exchange, message));
            }
            break;
        case WARN:
            if (log.isWarnEnabled()) {
                log.warn(logMessage(exchange, message));
            }
            break;
        case OFF:
            break;
        default:
            log.error("Unknown level: " + level + " when trying to log exchange: " + logMessage(exchange, message));
        }
    }

    public void log(String message, LoggingLevel loggingLevel) {
        LoggingLevel oldLogLevel = getLevel();
        setLevel(loggingLevel);
        log(message);
        setLevel(oldLogLevel);
    }
    
    public void log(String message) {
        switch (level) {
        case DEBUG:
            if (log.isDebugEnabled()) {
                log.debug(message);
            }
            break;
        case ERROR:
            if (log.isErrorEnabled()) {
                log.error(message);
            }
            break;
        case INFO:
            if (log.isInfoEnabled()) {
                log.info(message);
            }
            break;
        case TRACE:
            if (log.isTraceEnabled()) {
                log.trace(message);
            }
            break;
        case WARN:
            if (log.isWarnEnabled()) {
                log.warn(message);
            }
            break;
        case OFF:
            break;
        default:
            log.error("Unknown level: " + level + " when trying to log exchange: " + message);
        }
    }

    public void log(String message, Throwable exception, LoggingLevel loggingLevel) {
        LoggingLevel oldLogLevel = getLevel();
        setLevel(loggingLevel);
        log(message, exception);
        setLevel(oldLogLevel);
    }   
    
    public void log(String message, Throwable exception) {
        switch (level) {
        case DEBUG:
            if (log.isDebugEnabled()) {
                log.debug(message, exception);
            }
            break;
        case ERROR:
            if (log.isErrorEnabled()) {
                log.error(message, exception);
            }
            break;
        case INFO:
            if (log.isInfoEnabled()) {
                log.info(message, exception);
            }
            break;
        case TRACE:
            if (log.isTraceEnabled()) {
                log.trace(message, exception);
            }
            break;
        case WARN:
            if (log.isWarnEnabled()) {
                log.warn(message, exception);
            }
            break;
        case OFF:
            break;
        default:
            log.error("Unknown level: " + level + " when trying to log exchange: " + message, exception);
        }
    }

    protected String logMessage(Exchange exchange) {
        return formatter.format(exchange);
    }

    protected String logMessage(Exchange exchange, String message) {
        return formatter.format(exchange) + message;
    }

    public Logger getLog() {
        return log;
    }

    public void setLog(Logger log) {
        this.log = log;
    }

    public LoggingLevel getLevel() {
        return level;
    }

    public void setLevel(LoggingLevel level) {
        this.level = level;
    }

    public void setFormatter(ExchangeFormatter formatter) {
        this.formatter = formatter;
    }

    public void setLogName(String logName) {
        this.log = LoggerFactory.getLogger(logName);
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }
}
