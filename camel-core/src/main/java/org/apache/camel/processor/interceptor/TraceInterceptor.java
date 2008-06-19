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
package org.apache.camel.processor.interceptor;

import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.Logger;
import org.apache.camel.processor.LoggingLevel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An interceptor for debugging and tracing routes
 *
 * @version $Revision$
 */
public class TraceInterceptor extends DelegateProcessor implements ExchangeFormatter {
    private final ProcessorType node;
    private Predicate traceFilter;
    private boolean traceExceptions = true;
    private Logger logger = new Logger(LogFactory.getLog(TraceInterceptor.class), this);
    private TraceFormatter formatter;

    public TraceInterceptor(ProcessorType node, Processor target, TraceFormatter formatter) {
        super(target);
        this.node = node;
        this.formatter = formatter;
    }

    @Override
    public String toString() {
        return "TraceInterceptor[" + node + "]";
    }

    public void process(Exchange exchange) throws Exception {
        try {
            if (shouldLogExchange(exchange)) {
                logExchange(exchange);
            }
            super.proceed(exchange);
        } catch (Exception e) {
            logException(exchange, e);
            throw e;
        } catch (Error e) {
            logException(exchange, e);
            throw e;
        }
    }

    public Object format(Exchange exchange) {
        return formatter.format(this, exchange);
    }

    // Properties
    //-------------------------------------------------------------------------
    public ProcessorType getNode() {
        return node;
    }

    public Predicate getTraceFilter() {
        return traceFilter;
    }

    public void setTraceFilter(Predicate traceFilter) {
        this.traceFilter = traceFilter;
    }

    public boolean isTraceExceptions() {
        return traceExceptions;
    }

    public void setTraceExceptions(boolean traceExceptions) {
        this.traceExceptions = traceExceptions;
    }

    public Logger getLogger() {
        return logger;
    }

    public TraceFormatter getFormatter() {
        return formatter;
    }

    public void setFormatter(TraceFormatter formatter) {
        this.formatter = formatter;
    }

    public LoggingLevel getLevel() {
        return getLogger().getLevel();
    }

    public Log getLog() {
        return getLogger().getLog();
    }

    public void setLog(Log log) {
        getLogger().setLog(log);
    }

    public void setLevel(LoggingLevel level) {
        getLogger().setLevel(level);
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void logExchange(Exchange exchange) {
        logger.process(exchange);
    }

    protected void logException(Exchange exchange, Throwable throwable) {
        logger.process(exchange, throwable);
    }


    /**
     * Returns true if the given exchange should be logged in the trace list
     */
    protected boolean shouldLogExchange(Exchange exchange) {
        return traceFilter == null || traceFilter.matches(exchange);
    }

}