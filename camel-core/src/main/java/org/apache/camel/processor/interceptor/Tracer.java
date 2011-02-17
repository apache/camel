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
import org.apache.camel.Service;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.CamelLogger;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.InterceptStrategy;
import org.slf4j.LoggerFactory;

/**
 * An interceptor strategy for tracing routes
 *
 * @version 
 */
public class Tracer implements InterceptStrategy, Service {
    private static final String JPA_TRACE_EVENT_MESSAGE = "org.apache.camel.processor.interceptor.jpa.JpaTraceEventMessage";

    private TraceFormatter formatter = new DefaultTraceFormatter();
    private boolean enabled = true;
    private String logName = Tracer.class.getName();
    private LoggingLevel logLevel = LoggingLevel.INFO;
    private Predicate traceFilter;
    private boolean traceInterceptors;
    private boolean traceExceptions = true;
    private boolean logStackTrace;
    private boolean traceOutExchanges;
    private String destinationUri;
    private Endpoint destination;
    private boolean useJpa;
    private CamelLogger logger;
    private TraceInterceptorFactory traceInterceptorFactory = new DefaultTraceInterceptorFactory();
    private TraceEventHandler traceHandler;
    private String jpaTraceEventMessageClassName = JPA_TRACE_EVENT_MESSAGE;

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
                return (Tracer) interceptStrategy;
            }
        }
        return null;
    }

    /**
     * Gets the logger to be used for tracers that can format and log a given exchange.
     *
     * @param formatter the exchange formatter
     * @return the logger to use
     */
    public synchronized CamelLogger getLogger(ExchangeFormatter formatter) {
        if (logger == null) {
            logger = new CamelLogger(LoggerFactory.getLogger(getLogName()), formatter);
            logger.setLevel(getLogLevel());
        }
        return logger;
    }

    public Processor wrapProcessorInInterceptors(CamelContext context, ProcessorDefinition<?> definition,
                                                 Processor target, Processor nextTarget) throws Exception {
        // Force the creation of an id, otherwise the id is not available when the trace formatter is
        // outputting trace information
        definition.idOrCreate(context.getNodeIdFactory());
        return getTraceInterceptorFactory().createTraceInterceptor(definition, target, formatter, this);
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
        // update logger if its in use
        if (logger != null) {
            logger.setLevel(logLevel);
        }
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
        // update logger if its in use
        if (logger != null) {
            logger.setLogName(logName);
        }
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
     * has been completed.
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

    public TraceInterceptorFactory getTraceInterceptorFactory() {
        return this.traceInterceptorFactory;
    }

    /**
     * Set the factory to be used to create the trace interceptor.
     * It is expected that the factory will create a subclass of TraceInterceptor.
     * <p/>
     * Use this to take complete control of how trace events are handled.
     * The TraceInterceptorFactory should only be set before any routes are created, hence this
     * method is not thread safe.
     */
    public void setTraceInterceptorFactory(TraceInterceptorFactory traceInterceptorFactory) {
        this.traceInterceptorFactory = traceInterceptorFactory;
    }

    public TraceEventHandler getTraceHandler() {
        return traceHandler;
    }

    /**
     * Set the object to be used to perform tracing.
     * <p/>
     * Use this to take more control of how trace events are persisted.
     * Setting the traceHandler provides a simpler mechanism for controlling tracing
     * than the TraceInterceptorFactory.
     * The TraceHandler should only be set before any routes are created, hence this
     * method is not thread safe.
     */
    public void setTraceHandler(TraceEventHandler traceHandler) {
        this.traceHandler = traceHandler;
    }

    public String getJpaTraceEventMessageClassName() {
        return jpaTraceEventMessageClassName;
    }

    /**
     * Set the fully qualified name of the class to be used by the JPA event tracing.
     * <p/>
     * The class must exist in the classpath and be available for dynamic loading.
     * The class name should only be set before any routes are created, hence this
     * method is not thread safe.
     */
    public void setJpaTraceEventMessageClassName(String jpaTraceEventMessageClassName) {
        this.jpaTraceEventMessageClassName = jpaTraceEventMessageClassName;
    }

    @Override
    public String toString() {
        return "Tracer";
    }

    public void start() throws Exception {
    }

    public void stop() throws Exception {
    }
}
