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

import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.spi.InterceptStrategy;

/**
 * An interceptor strategy for tracing routes
 *
 * @version $Revision$
 */
public class Tracer implements InterceptStrategy {

    private TraceFormatter formatter = new DefaultTraceFormatter();
    private boolean enabled = true;
    private String logName;
    private LoggingLevel logLevel;
    private Predicate traceFilter;
    private boolean traceInterceptors;
    private boolean traceExceptions = true;
    private boolean logStackTrace;
    private boolean traceOutExchanges;
    private String destinationUri;
    private Endpoint destination;
    private boolean useJpa;

    /**
     * Creates a new tracer.
     *
     * @param context Camel context
     * @return a new tracer
     */
    public static Tracer createTracer(CamelContext context) {
        Tracer tracer = new Tracer();
        // lets see if we have a formatter if so use it
        TraceFormatter formatter = context.getRegistry().lookup("traceFormatter", TraceFormatter.class);
        if (formatter != null) {
            tracer.setFormatter(formatter);
        }
        return tracer;
    }

    /**
     * A helper method to return the Tracer instance if one is enabled
     *
     * @return the tracer or null if none can be found
     */
    public static Tracer getTracer(CamelContext context) {
        List<InterceptStrategy> list = context.getInterceptStrategies();
        for (InterceptStrategy interceptStrategy : list) {
            if (interceptStrategy instanceof Tracer) {
                return (Tracer)interceptStrategy;
            }
        }
        return null;
    }

    public Processor wrapProcessorInInterceptors(CamelContext context, ProcessorDefinition definition,
                                                 Processor target, Processor nextTarget) throws Exception {
        // Force the creation of an id, otherwise the id is not available when the trace formatter is
        // outputting trace information
        definition.idOrCreate(context.getNodeIdFactory());
        return new TraceInterceptor(definition, target, formatter, this);
    }

    public TraceFormatter getFormatter() {
        return formatter;
    }

    public DefaultTraceFormatter getDefaultTraceFormatter() {
        if (formatter instanceof DefaultTraceFormatter) {
            return (DefaultTraceFormatter) formatter;
        }
        return null;
    }

    public void setFormatter(TraceFormatter formatter) {
        this.formatter = formatter;
    }

    public void setEnabled(boolean flag) {
        enabled = flag;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isTraceInterceptors() {
        return traceInterceptors;
    }

    /**
     * Sets whether interceptors should be traced or not
     */
    public void setTraceInterceptors(boolean traceInterceptors) {
        this.traceInterceptors = traceInterceptors;
    }

    public Predicate getTraceFilter() {
        return traceFilter;
    }

    /**
     * Sets a predicate to be used as filter when tracing
     */
    public void setTraceFilter(Predicate traceFilter) {
        this.traceFilter = traceFilter;
    }

    public LoggingLevel getLogLevel() {
        return logLevel;
    }

    /**
     * Sets the logging level to output tracing. Will use <tt>INFO</tt> level by default.
     */
    public void setLogLevel(LoggingLevel logLevel) {
        this.logLevel = logLevel;
    }

    public boolean isTraceExceptions() {
        return traceExceptions;
    }

    /**
     * Sets whether thrown exceptions should be traced
     */
    public void setTraceExceptions(boolean traceExceptions) {
        this.traceExceptions = traceExceptions;
    }

    public boolean isLogStackTrace() {
        return logStackTrace;
    }

    /**
     * Sets whether thrown exception stacktrace should be traced, if disabled then only the exception message is logged
     */
    public void setLogStackTrace(boolean logStackTrace) {
        this.logStackTrace = logStackTrace;
    }

    public String getLogName() {
        return logName;
    }

    /**
     * Sets the logging name to use.
     * Will default use <tt>org.apache.camel.processor.interceptor.TraceInterceptor<tt>.
     */
    public void setLogName(String logName) {
        this.logName = logName;
    }

    /**
     * Sets whether exchanges coming out of processors should be traced
     */    
    public void setTraceOutExchanges(boolean traceOutExchanges) {
        this.traceOutExchanges = traceOutExchanges;
    }
    
    public boolean isTraceOutExchanges() {
        return traceOutExchanges;
    }

    public String getDestinationUri() {
        return destinationUri;
    }

    /**
     * Sets an optional destination to send the traced Exchange.
     * <p/>
     * Can be used to store tracing as files, in a database or whatever. The routing of the Exchange
     * will happen synchronously and the original route will first continue when this destination routing
     * has been compledted.
     */
    public void setDestinationUri(String destinationUri) {
        this.destinationUri = destinationUri;
    }

    public Endpoint getDestination() {
        return destination;
    }

    /**
     * See {@link #setDestinationUri(String)}
     */
    public void setDestination(Endpoint destination) {
        this.destination = destination;
    }

    public boolean isUseJpa() {
        return useJpa;
    }

    /**
     * Sets whether we should use a JpaTraceEventMessage instead of
     * an ordinary {@link org.apache.camel.processor.interceptor.DefaultTraceEventMessage}
     * <p/>
     * Use this to allow persistence of trace events into a database using JPA.
     * This requires camel-jpa in the classpath.
     */
    public void setUseJpa(boolean useJpa) {
        this.useJpa = useJpa;
    }

    @Override
    public String toString() {
        return "Tracer";
    }
}
