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
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.Service;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
public class DefaultTraceEventHandler implements TraceEventHandler, Service {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultTraceEventHandler.class);
    
    private Producer traceEventProducer;
    private Class<?> jpaTraceEventMessageClass;
    private String jpaTraceEventMessageClassName;

    private final Tracer tracer;
    
    public DefaultTraceEventHandler(Tracer tracer) {
        this.tracer = tracer;
    }

    private synchronized void loadJpaTraceEventMessageClass(Exchange exchange) {
        if (jpaTraceEventMessageClass == null) {
            jpaTraceEventMessageClassName = tracer.getJpaTraceEventMessageClassName();
        }
        if (jpaTraceEventMessageClass == null) {
            jpaTraceEventMessageClass = exchange.getContext().getClassResolver().resolveClass(jpaTraceEventMessageClassName);
            if (jpaTraceEventMessageClass == null) {
                throw new IllegalArgumentException("Cannot find class: " + jpaTraceEventMessageClassName
                        + ". Make sure camel-jpa.jar is in the classpath.");
            }
        }
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
    public void traceExchange(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
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
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Using class: {} for tracing event messages", this.jpaTraceEventMessageClassName);
                }

                // load the jpa event message class
                loadJpaTraceEventMessageClass(exchange);
                // create a new instance of the event message class
                Object jpa = ObjectHelper.newInstance(jpaTraceEventMessageClass);

                // copy options from event to jpa
                Map<String, Object> options = new HashMap<String, Object>();
                IntrospectionSupport.getProperties(msg, options, null);
                IntrospectionSupport.setProperties(exchange.getContext().getTypeConverter(), jpa, options);
                // and set the timestamp as its not a String type
                IntrospectionSupport.setProperty(exchange.getContext().getTypeConverter(), jpa, "timestamp", msg.getTimestamp());

                event.getIn().setBody(jpa);
            } else {
                event.getIn().setBody(msg);
            }

            // marker property to indicate its a tracing event being routed in case
            // new Exchange instances is created during trace routing so we can check
            // for this marker when interceptor also kick in during routing of trace events
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

    @Override
    public Object traceExchangeIn(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange) throws Exception {
        traceExchange(node, target, traceInterceptor, exchange);
        return null;
    }

    @Override
    public void traceExchangeOut(ProcessorDefinition<?> node, Processor target, TraceInterceptor traceInterceptor, Exchange exchange, Object traceState) throws Exception {
        traceExchange(node, target, traceInterceptor, exchange);
    }

    @Override
    public void start() throws Exception {
        traceEventProducer = null;
    }

    @Override
    public void stop() throws Exception {
        if (traceEventProducer != null) {
            ServiceHelper.stopService(traceEventProducer);
        }
    }

}
