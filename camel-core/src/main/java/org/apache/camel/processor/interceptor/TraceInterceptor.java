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

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.model.InterceptorRef;
import org.apache.camel.model.ProcessorType;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.Logger;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * An interceptor for debugging and tracing routes
 *
 * @version $Revision$
 */
public class TraceInterceptor extends DelegateProcessor implements ExchangeFormatter {
    private static final transient Log LOG = LogFactory.getLog(TraceInterceptor.class);
    private static final String TRACE_EVENT = "CamelTraceEvent";
    private Logger logger;
    private Producer traceEventProducer;
    private final ProcessorType node;
    private final Tracer tracer;
    private TraceFormatter formatter;

    public TraceInterceptor(ProcessorType node, Processor target, TraceFormatter formatter, Tracer tracer) {
        super(target);
        this.tracer = tracer;
        this.node = node;
        this.formatter = formatter;

        // set logger to use
        if (tracer.getLogName() != null) {
            logger = new Logger(LogFactory.getLog(tracer.getLogName()), this);
        } else {
            // use default logger
            logger = new Logger(LogFactory.getLog(TraceInterceptor.class), this);
        }

        // set logging level if provided
        if (tracer.getLogLevel() != null) {
            logger.setLevel(tracer.getLogLevel());
        }

        if (tracer.getFormatter() != null) {
            this.formatter = tracer.getFormatter();
        }
    }

    public TraceInterceptor(ProcessorType node, Processor target, Tracer tracer) {
        this(node, target, null, tracer);
    }

    @Override
    public String toString() {
        return "TraceInterceptor[" + node + "]";
    }

    public void process(final Exchange exchange) throws Exception {
        // interceptor will also trace routes supposed only for TraceEvents so we need to skip
        // logging TraceEvents to avoid infinite looping
        if (exchange instanceof TraceEventExchange || exchange.getProperty(TRACE_EVENT, Boolean.class) != null) {
            // but we must still process to allow routing of TraceEvents to eg a JPA endpoint
            super.process(exchange);
            return;
        }

        // okay this is a regular exchange being routed we might need to log and trace
        try {
            // before
            if (shouldLogNode(node) && shouldLogExchange(exchange)) {
                logExchange(exchange);
                traceExchange(exchange);
            }

            // process the exchange
            super.proceed(exchange);

            // after (trace out)
            if (tracer.isTraceOutExchanges() && shouldLogNode(node) && shouldLogExchange(exchange)) {
                logExchange(exchange);
                traceExchange(exchange);
            }
        } catch (Exception e) {
            if (shouldLogException(exchange)) {
                logException(exchange, e);
            }
            throw e;
        }
    }

    public Object format(Exchange exchange) {
        return formatter.format(this, this.getNode(), exchange);
    }

    // Properties
    //-------------------------------------------------------------------------
    public ProcessorType getNode() {
        return node;
    }

    public Logger getLogger() {
        return logger;
    }

    public TraceFormatter getFormatter() {
        return formatter;
    }

    // Implementation methods
    //-------------------------------------------------------------------------
    protected void logExchange(Exchange exchange) {
        // process the exchange that formats and logs it
        logger.process(exchange);
    }

    protected void traceExchange(Exchange exchange) throws Exception {
        // should we send a trace event to an optional destination?
        if (tracer.getDestinationUri() != null) {
            // create event and add it as a property on the original exchange
            TraceEventExchange event = new TraceEventExchange(exchange);
            event.setNodeId(node.getId());
            event.setTimestamp(new Date());
            event.setTracedExchange(exchange);

            // create event message to send in body
            TraceEventMessage msg = new TraceEventMessage(node, exchange);
            event.getIn().setBody(msg);
            // marker property to indicate its a tracing event being routed in case
            // new Exchange instances is created during trace routing so we can check
            // for this marker when interceptor also kickins in during routing of trace events
            event.setProperty(TRACE_EVENT, Boolean.TRUE);
            try {
                // process the trace route
                getTraceEventProducer(exchange).process(event);
            } catch (Exception e) {
                // log and ignore this as the original Exchange should be allowed to continue
                LOG.error("Error processing TraceEventExchange (original Exchange will be continued): " + event, e);
            }
        }
    }

    protected void logException(Exchange exchange, Throwable throwable) {
        if (tracer.isTraceExceptions()) {
            logger.process(exchange, throwable);
        }
    }

    /**
     * Returns true if the given exchange should be logged in the trace list
     */
    protected boolean shouldLogExchange(Exchange exchange) {
        return tracer.isEnabled() && (tracer.getTraceFilter() == null || tracer.getTraceFilter().matches(exchange));
    }

    /**
     * Returns true if the given exchange should be logged when an exception was thrown
     */
    protected boolean shouldLogException(Exchange exchange) {
        return tracer.isTraceExceptions();
    }

    /**
     * Returns whether exchanges coming out of processors should be traced
     */
    public boolean shouldTraceOutExchanges() {
        return tracer.isTraceOutExchanges();
    }

    /**
     * Returns true if the given node should be logged in the trace list
     */
    protected boolean shouldLogNode(ProcessorType node) {
        if (node == null) {
            return false;
        }
        if (!tracer.isTraceInterceptors() && (node instanceof InterceptStrategy || node instanceof InterceptorRef)) {
            return false;
        }
        return true;
    }

    private synchronized Producer getTraceEventProducer(Exchange exchange) throws Exception {
        if (traceEventProducer == null) {
            // create producer when we have access the the camel context (we dont in doStart)
            traceEventProducer = exchange.getContext().getEndpoint(tracer.getDestinationUri()).createProducer();
            ServiceHelper.startService(traceEventProducer);
        }
        return traceEventProducer;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        traceEventProducer = null;
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (traceEventProducer != null) {
            ServiceHelper.stopService(traceEventProducer);
        }
    }

}