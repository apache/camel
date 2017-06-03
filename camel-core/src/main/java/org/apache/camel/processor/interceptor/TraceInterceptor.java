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

import java.util.Collections;
import java.util.List;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.AggregateRouteNode;
import org.apache.camel.impl.DefaultRouteNode;
import org.apache.camel.impl.DoCatchRouteNode;
import org.apache.camel.impl.DoFinallyRouteNode;
import org.apache.camel.impl.OnCompletionRouteNode;
import org.apache.camel.impl.OnExceptionRouteNode;
import org.apache.camel.model.AggregateDefinition;
import org.apache.camel.model.CatchDefinition;
import org.apache.camel.model.Constants;
import org.apache.camel.model.FinallyDefinition;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.processor.CamelLogProcessor;
import org.apache.camel.processor.DefaultMaskingFormatter;
import org.apache.camel.processor.DelegateAsyncProcessor;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.MaskingFormatter;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.spi.TracedRouteNodes;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An interceptor for debugging and tracing routes
 *
 * @version 
 */
@Deprecated
public class TraceInterceptor extends DelegateAsyncProcessor implements ExchangeFormatter {
    private static final Logger LOG = LoggerFactory.getLogger(TraceInterceptor.class);

    private CamelLogProcessor logger;

    private final ProcessorDefinition<?> node;
    private final Tracer tracer;
    private TraceFormatter formatter;

    private RouteContext routeContext;
    private List<TraceEventHandler> traceHandlers;

    public TraceInterceptor(ProcessorDefinition<?> node, Processor target, TraceFormatter formatter, Tracer tracer) {
        super(target);
        this.tracer = tracer;
        this.node = node;
        this.formatter = formatter;
        this.logger = tracer.getLogger(this);
        if (tracer.getFormatter() != null) {
            this.formatter = tracer.getFormatter();
        }
        this.traceHandlers = tracer.getTraceHandlers();
    }

    @Override
    public String toString() {
        return "TraceInterceptor[" + node + "]";
    }

    public void setRouteContext(RouteContext routeContext) {
        this.routeContext = routeContext;
        prepareMaskingFormatter(routeContext);
    }

    private void prepareMaskingFormatter(RouteContext routeContext) {
        if (routeContext.isLogMask()) {
            MaskingFormatter formatter = routeContext.getCamelContext().getRegistry().lookupByNameAndType(Constants.CUSTOM_LOG_MASK_REF, MaskingFormatter.class);
            if (formatter == null) {
                formatter = new DefaultMaskingFormatter();
            }
            logger.setMaskingFormatter(formatter);
        }
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        // do not trace if tracing is disabled
        if (!tracer.isEnabled() || (routeContext != null && !routeContext.isTracing())) {
            return processor.process(exchange, callback);
        }

        // interceptor will also trace routes supposed only for TraceEvents so we need to skip
        // logging TraceEvents to avoid infinite looping
        if (exchange.getProperty(Exchange.TRACE_EVENT, false, Boolean.class)) {
            // but we must still process to allow routing of TraceEvents to eg a JPA endpoint
            return processor.process(exchange, callback);
        }

        final boolean shouldLog = shouldLogNode(node) && shouldLogExchange(exchange);

        // whether we should trace it or not, some nodes should be skipped as they are abstract
        // intermediate steps for instance related to on completion
        boolean trace = true;
        boolean sync = true;

        // okay this is a regular exchange being routed we might need to log and trace
        try {
            // before
            if (shouldLog) {
                // traced holds the information about the current traced route path
                if (exchange.getUnitOfWork() != null) {
                    TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();
                    if (traced != null) {
                        if (node instanceof OnCompletionDefinition || node instanceof OnExceptionDefinition) {
                            // skip any of these as its just a marker definition
                            trace = false;
                        } else if (ProcessorDefinitionHelper.isFirstChildOfType(OnCompletionDefinition.class, node)) {
                            // special for on completion tracing
                            traceOnCompletion(traced, exchange);
                        } else if (ProcessorDefinitionHelper.isFirstChildOfType(OnExceptionDefinition.class, node)) {
                            // special for on exception
                            traceOnException(traced, exchange);
                        } else if (ProcessorDefinitionHelper.isFirstChildOfType(CatchDefinition.class, node)) {
                            // special for do catch
                            traceDoCatch(traced, exchange);
                        } else if (ProcessorDefinitionHelper.isFirstChildOfType(FinallyDefinition.class, node)) {
                            // special for do finally
                            traceDoFinally(traced, exchange);
                        } else if (ProcessorDefinitionHelper.isFirstChildOfType(AggregateDefinition.class, node)) {
                            // special for aggregate
                            traceAggregate(traced, exchange);
                        } else {
                            // regular so just add it
                            traced.addTraced(new DefaultRouteNode(node, super.getProcessor()));
                        }
                    }
                } else {
                    LOG.trace("Cannot trace as this Exchange does not have an UnitOfWork: {}", exchange);
                }
            }

            // log and trace the processor
            Object state = null;
            if (shouldLog && trace) {
                logExchange(exchange);
                // either call the in or generic trace method depending on OUT has been enabled or not
                if (tracer.isTraceOutExchanges()) {
                    state = traceExchangeIn(exchange);
                } else {
                    traceExchange(exchange);
                }
            }
            final Object traceState = state;

            // special for interceptor where we need to keep booking how far we have routed in the intercepted processors
            if (node.getParent() instanceof InterceptDefinition && exchange.getUnitOfWork() != null) {
                TracedRouteNodes traced = exchange.getUnitOfWork().getTracedRouteNodes();
                if (traced != null) {
                    traceIntercept((InterceptDefinition) node.getParent(), traced, exchange);
                }
            }

            // process the exchange
            sync = processor.process(exchange, new AsyncCallback() {
                @Override
                public void done(boolean doneSync) {
                    try {
                        // after (trace out)
                        if (shouldLog && tracer.isTraceOutExchanges()) {
                            logExchange(exchange);
                            traceExchangeOut(exchange, traceState);
                        }
                    } catch (Throwable e) {
                        // some exception occurred in trace logic
                        if (shouldLogException(exchange)) {
                            logException(exchange, e);
                        }
                        exchange.setException(e);
                    } finally {
                        // ensure callback is always invoked
                        callback.done(doneSync);
                    }
                }
            });

        } catch (Throwable e) {
            // some exception occurred in trace logic
            if (shouldLogException(exchange)) {
                logException(exchange, e);
            }
            exchange.setException(e);
        }

        return sync;
    }

    private void traceOnCompletion(TracedRouteNodes traced, Exchange exchange) {
        traced.addTraced(new OnCompletionRouteNode());
        // do not log and trace as onCompletion should be a new event on its own
        // add the next step as well so we have onCompletion -> new step
        traced.addTraced(new DefaultRouteNode(node, super.getProcessor()));
    }

    private void traceOnException(TracedRouteNodes traced, Exchange exchange) throws Exception {
        if (traced.getLastNode() != null) {
            traced.addTraced(new DefaultRouteNode(traced.getLastNode().getProcessorDefinition(), traced.getLastNode().getProcessor()));
        }
        traced.addTraced(new OnExceptionRouteNode());
        // log and trace so we have the from -> onException event as well
        logExchange(exchange);
        traceExchange(exchange);
        traced.addTraced(new DefaultRouteNode(node, super.getProcessor()));
    }

    private void traceDoCatch(TracedRouteNodes traced, Exchange exchange) throws Exception {
        if (traced.getLastNode() != null) {
            traced.addTraced(new DefaultRouteNode(traced.getLastNode().getProcessorDefinition(), traced.getLastNode().getProcessor()));
        }
        traced.addTraced(new DoCatchRouteNode());
        // log and trace so we have the from -> doCatch event as well
        logExchange(exchange);
        traceExchange(exchange);
        traced.addTraced(new DefaultRouteNode(node, super.getProcessor()));
    }

    private void traceDoFinally(TracedRouteNodes traced, Exchange exchange) throws Exception {
        if (traced.getLastNode() != null) {
            traced.addTraced(new DefaultRouteNode(traced.getLastNode().getProcessorDefinition(), traced.getLastNode().getProcessor()));
        }
        traced.addTraced(new DoFinallyRouteNode());
        // log and trace so we have the from -> doFinally event as well
        logExchange(exchange);
        traceExchange(exchange);
        traced.addTraced(new DefaultRouteNode(node, super.getProcessor()));
    }

    private void traceAggregate(TracedRouteNodes traced, Exchange exchange) {
        traced.addTraced(new AggregateRouteNode((AggregateDefinition) node.getParent()));
        traced.addTraced(new DefaultRouteNode(node, super.getProcessor()));
    }

    protected void traceIntercept(InterceptDefinition intercept, TracedRouteNodes traced, Exchange exchange) throws Exception {
        // use the counter to get the index of the intercepted processor to be traced
        Processor last = intercept.getInterceptedProcessor(traced.getAndIncrementCounter(intercept));
        // skip doing any double tracing of interceptors, so the last must not be a TraceInterceptor instance
        if (last != null && !(last instanceof TraceInterceptor)) {
            traced.addTraced(new DefaultRouteNode(node, last));

            boolean shouldLog = shouldLogNode(node) && shouldLogExchange(exchange);
            if (shouldLog) {
                // log and trace the processor that was intercepted so we can see it
                logExchange(exchange);
                traceExchange(exchange);
            }
        }
    }

    public String format(Exchange exchange) {
        Object msg = formatter.format(this, this.getNode(), exchange);
        if (msg != null) {
            return msg.toString();
        } else {
            return null;
        }
    }

    // Properties
    //-------------------------------------------------------------------------
    public ProcessorDefinition<?> getNode() {
        return node;
    }

    public CamelLogProcessor getLogger() {
        return logger;
    }

    public TraceFormatter getFormatter() {
        return formatter;
    }

    public Tracer getTracer() {
        return tracer;
    }

    protected void logExchange(Exchange exchange) throws Exception {
        // process the exchange that formats and logs it
        logger.process(exchange);
    }

    protected void traceExchange(Exchange exchange) throws Exception {
        for (TraceEventHandler traceHandler : traceHandlers) {
            traceHandler.traceExchange(node, processor, this, exchange);
        }
    }

    protected Object traceExchangeIn(Exchange exchange) throws Exception {
        Object result = null;
        for (TraceEventHandler traceHandler : traceHandlers) {
            Object result1 = traceHandler.traceExchangeIn(node, processor, this, exchange);
            if (result1 != null) {
                result = result1;
            }
        }
        return result;
    }

    protected void traceExchangeOut(Exchange exchange, Object traceState) throws Exception {
        for (TraceEventHandler traceHandler : traceHandlers) {
            traceHandler.traceExchangeOut(node, processor, this, exchange, traceState);
        }
    }

    protected void logException(Exchange exchange, Throwable throwable) {
        if (tracer.isTraceExceptions()) {
            if (tracer.isLogStackTrace()) {
                logger.process(exchange, throwable);
            } else {
                logger.process(exchange, ", Exception: " + throwable.toString());
            }
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
    protected boolean shouldLogNode(ProcessorDefinition<?> node) {
        if (node == null) {
            return false;
        }
        if (!tracer.isTraceInterceptors() && (node instanceof InterceptStrategy)) {
            return false;
        }
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        ServiceHelper.startService(traceHandlers);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        ServiceHelper.stopService(traceHandlers);
    }

    @Deprecated
    public void setTraceHandler(TraceEventHandler traceHandler) {
        traceHandlers = Collections.singletonList(traceHandler);
    }
}
