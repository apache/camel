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
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Expression;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultRouteNode;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.model.InterceptDefinition;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.DelegateProcessor;
import org.apache.camel.processor.Logger;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.TraceableUnitOfWork;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
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
    private static final String JPA_TRACE_EVENT_MESSAGE = "org.apache.camel.processor.interceptor.JpaTraceEventMessage";
    private Logger logger;
    private Producer traceEventProducer;
    private final ProcessorDefinition node;
    private final Tracer tracer;
    private TraceFormatter formatter;
    private Class jpaTraceEventMessageClass;

    public TraceInterceptor(ProcessorDefinition node, Processor target, TraceFormatter formatter, Tracer tracer) {
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

    public TraceInterceptor(ProcessorDefinition node, Processor target, Tracer tracer) {
        this(node, target, null, tracer);
    }

    @Override
    public String toString() {
        return "TraceInterceptor[" + node + "]";
    }

    public void process(final Exchange exchange) throws Exception {
        // interceptor will also trace routes supposed only for TraceEvents so we need to skip
        // logging TraceEvents to avoid infinite looping
        if (exchange.getProperty(Exchange.TRACE_EVENT, Boolean.class) != null) {
            // but we must still process to allow routing of TraceEvents to eg a JPA endpoint
            super.process(exchange);
            return;
        }

        boolean shouldLog = shouldLogNode(node) && shouldLogExchange(exchange);

        // whether we should trace it or not, some nodes should be skipped as they are abstract
        // intermedidate steps for instance related to on completion
        boolean trace = true;

        // okay this is a regular exchange being routed we might need to log and trace
        try {
            // before
            if (shouldLog) {

                // register route path taken if TraceableUnitOfWork unit of work
                if (exchange.getUnitOfWork() instanceof TraceableUnitOfWork) {
                    TraceableUnitOfWork tuow = (TraceableUnitOfWork) exchange.getUnitOfWork();

                    if (node instanceof OnExceptionDefinition) {
                        // special for on exception so we can see it in the trace logs
                        trace = beforeOnException((OnExceptionDefinition) node, tuow, exchange);
                    } else if (node instanceof OnCompletionDefinition) {
                        // special for on completion so we can see it in the trace logs
                        trace = beforeOnCompletion((OnCompletionDefinition) node, tuow, exchange);
                    } else {
                        // regular so just add it
                        tuow.addTraced(new DefaultRouteNode(node, super.getProcessor()));
                    }
                }
            }

            // log and trace the processor
            if (trace) {
                logExchange(exchange);
                traceExchange(exchange);
            }

            // some nodes need extra work to trace it
            if (exchange.getUnitOfWork() instanceof TraceableUnitOfWork) {
                TraceableUnitOfWork tuow = (TraceableUnitOfWork) exchange.getUnitOfWork();

                if (node instanceof InterceptDefinition) {
                    // special for intercept() as we would like to trace the processor that was intercepted
                    // as well, otherwise we only see the intercepted route, but we need the both to be logged/traced
                    afterIntercept((InterceptDefinition) node, tuow, exchange);
                }
            }

            // process the exchange
            super.proceed(exchange);

            // after (trace out)
            if (shouldLog && tracer.isTraceOutExchanges()) {
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
    public ProcessorDefinition getNode() {
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
    protected boolean beforeOnException(OnExceptionDefinition onException, TraceableUnitOfWork tuow, Exchange exchange) throws Exception {
        // lets see if this is the first time for this exception
        int index = tuow.getAndIncrement(node);
        if (index == 0) {
            class OnExceptionExpression implements Expression {
                @SuppressWarnings("unchecked")
                public Object evaluate(Exchange exchange, Class type) {
                    String label = "OnException";
                    if (exchange.getProperty(Exchange.EXCEPTION_CAUGHT) != null) {
                        label += "[" + exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class).getClass().getSimpleName() + "]";
                    }
                    return exchange.getContext().getTypeConverter().convertTo(type, label);
                }
                
            }
            // yes its first time then do some special to log and trace the
            // start of onException
            Expression exp = new OnExceptionExpression(); 
              
            // add our pesudo node
            tuow.addTraced(new DefaultRouteNode(node, exp));

            // log and trace the processor that was onException so we can see immediately
            logExchange(exchange);
            traceExchange(exchange);
        }

        // add the processor that is invoked for this onException
        tuow.addTraced(new DefaultRouteNode(node, super.getProcessor()));
        return true;
    }
    

    protected boolean beforeOnCompletion(OnCompletionDefinition onCompletion, TraceableUnitOfWork tuow, Exchange exchange) throws Exception {
        // we should only trace when we do the actual onCompletion
        // the problem is that onCompletion is added at the very beginning of a route to be able to
        // add synchronization hoos on unit of work so it knows to invoke the onCompletion when the
        // exchange is done. But in the trace log we want to defer the onCompletion being logged
        // unitl the exchange is actually completed and is doing the onCompletion routing
        // so if the last node is null then we have just started and thus should not trace this node
        boolean answer = tuow.getLastNode() != null;

        if (exchange.getProperty(Exchange.ON_COMPLETION) != null) {
            // if ON_COMPLETION is not null then we are actually doing the onCompletion routing
            
            // we should trace the onCompletion route and we want a start log of the onCompletion
            // step so get the index and see if its 0 then we can add our speical log
            int index = tuow.getAndIncrement(node);
            if (index == 0) {
                class OnCompletionExpression implements Expression {
                    @SuppressWarnings("unchecked")
                    public Object evaluate(Exchange exchange, Class type) {
                        String label = "OnCompletion[" + exchange.getProperty(Exchange.CORRELATION_ID) + "]";
                        return exchange.getContext().getTypeConverter().convertTo(type, label);
                    }
                }
                // yes its first time then do some special to log and trace the start of onCompletion
                Expression exp = new OnCompletionExpression();
                // add the onCompletion and then the processor that is invoked nest
                tuow.addTraced(new DefaultRouteNode(node, exp));
                tuow.addTraced(new DefaultRouteNode(node, super.getProcessor()));

                // log and trace so we get the onCompletion -> processor in the log
                logExchange(exchange);
                traceExchange(exchange);
            } else {
                // we are doing the onCompletion but this is after the start so just
                // add the processor and do no special start message
                tuow.addTraced(new DefaultRouteNode(node, super.getProcessor()));
            }

        }

        return answer;
    }

    protected boolean afterIntercept(InterceptDefinition interceptr, TraceableUnitOfWork tuow, Exchange exchange) throws Exception {
        // get the intercepted processor from the definition
        // we need to use the UoW to have its own index of how far we got into the list
        // of intercepted processors the intercept definition holds as the intercept
        // definition is a single object that is shared by concurrent thread being routed
        // so each exchange has its own private counter
        InterceptDefinition intercept = (InterceptDefinition) node;
        Processor last = intercept.getInterceptedProcessor(tuow.getAndIncrement(intercept));
        if (last != null) {
            tuow.addTraced(new DefaultRouteNode(node, last));

            // log and trace the processor that was intercepted so we can see it
            logExchange(exchange);
            traceExchange(exchange);
        }

        return true;
    }

    protected void logExchange(Exchange exchange) {
        // process the exchange that formats and logs it
        logger.process(exchange);
    }

    @SuppressWarnings("unchecked")
    protected void traceExchange(Exchange exchange) throws Exception {
        // should we send a trace event to an optional destination?
        if (tracer.getDestination() != null || tracer.getDestinationUri() != null) {

            // create event exchange and add event information
            Date timestamp = new Date();
            Exchange event = new DefaultExchange(exchange);
            event.setProperty(Exchange.TRACE_EVENT_NODE_ID, node.getId());
            event.setProperty(Exchange.TRACE_EVENT_TIMESTAMP, timestamp);
            // keep a reference to the original exchange in case its needed
            event.setProperty(Exchange.TRACE_EVENT_EXCHANGE, exchange);

            // create event message to sent as in body containing event information such as
            // from node, to node, etc.
            TraceEventMessage msg = new DefaultTraceEventMessage(timestamp, node, exchange);

            // should we use ordinary or jpa objects
            if (tracer.isUseJpa()) {
                LOG.trace("Using class: " + JPA_TRACE_EVENT_MESSAGE + " for tracing event messages");

                // load the jpa event class
                synchronized (this) {
                    if (jpaTraceEventMessageClass == null) {
                        jpaTraceEventMessageClass = exchange.getContext().getClassResolver().resolveClass(JPA_TRACE_EVENT_MESSAGE);
                        if (jpaTraceEventMessageClass == null) {
                            throw new IllegalArgumentException("Cannot find class: " + JPA_TRACE_EVENT_MESSAGE
                                    + ". Make sure camel-jpa.jar is in the classpath.");
                        }
                    }
                }

                Object jpa = ObjectHelper.newInstance(jpaTraceEventMessageClass);

                // copy options from event to jpa
                Map options = new HashMap();
                IntrospectionSupport.getProperties(msg, options, null);
                IntrospectionSupport.setProperties(jpa, options);
                // and set the timestamp as its not a String type
                IntrospectionSupport.setProperty(jpa, "timestamp", msg.getTimestamp());

                event.getIn().setBody(jpa);
            } else {
                event.getIn().setBody(msg);
            }

            // marker property to indicate its a tracing event being routed in case
            // new Exchange instances is created during trace routing so we can check
            // for this marker when interceptor also kickins in during routing of trace events
            event.setProperty(Exchange.TRACE_EVENT, Boolean.TRUE);
            try {
                // process the trace route
                getTraceEventProducer(exchange).process(event);
            } catch (Exception e) {
                // log and ignore this as the original Exchange should be allowed to continue
                LOG.error("Error processing trace event (original Exchange will continue): " + event, e);
            }
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
    protected boolean shouldLogNode(ProcessorDefinition node) {
        if (node == null) {
            return false;
        }
        if (!tracer.isTraceInterceptors() && (node instanceof InterceptStrategy)) {
            return false;
        }
        return true;
    }

    private synchronized Producer getTraceEventProducer(Exchange exchange) throws Exception {
        if (traceEventProducer == null) {
            // create producer when we have access the the camel context (we dont in doStart)
            Endpoint endpoint = tracer.getDestination() != null ? tracer.getDestination() : exchange.getContext().getEndpoint(tracer.getDestinationUri());
            traceEventProducer = endpoint.createProducer();
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
