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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.impl.ServiceSupport;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.processor.interceptor.TraceInterceptor;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.LifecycleStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ServiceHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * DefaultChannel is the default {@link Channel}.
 * <p/>
 * The current implementation is just a composite containing the interceptors and error handler
 * that beforehand was added to the route graph directly.
 * <br/>
 * With this {@link Channel} we can in the future implement better strategies for routing the
 * {@link Exchange} in the route graph, as we have a {@link Channel} between each and every node
 * in the graph.
 *
 * @version $Revision$
 */
public class DefaultChannel extends ServiceSupport implements Processor, Channel {

    private static final transient Log LOG = LogFactory.getLog(DefaultChannel.class);

    private final List<InterceptStrategy> interceptors = new ArrayList<InterceptStrategy>();
    private Processor errorHandler;
    // the next processor (non wrapped)
    private Processor nextProcessor;
    // the real output to invoke that has been wrapped
    private Processor output;
    private ProcessorDefinition definition;
    private RouteContext routeContext;
    private CamelContext camelContext;

    public List<Processor> next() {
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(nextProcessor);
        return answer;
    }

    public boolean hasNext() {
        return nextProcessor != null;
    }

    public void setNextProcessor(Processor next) {
        this.nextProcessor = next;
    }

    public Processor getOutput() {
        // the errorHandler is already decorated with interceptors
        // so it cointain the entire chain of processors, so we can safely use it directly as output
        // if no error handler provided we use the output
        return errorHandler != null ? errorHandler : output;
    }

    public void setOutput(Processor output) {
        this.output = output;
    }

    public Processor getNextProcessor() {
        return nextProcessor;
    }

    public boolean hasInterceptorStrategy(Class type) {
        for (InterceptStrategy strategy : interceptors) {
            if (type.isInstance(strategy)) {
                return true;
            }
        }
        return false;
    }

    public void setErrorHandler(Processor errorHandler) {
        this.errorHandler = errorHandler;
    }

    public Processor getErrorHandler() {
        return errorHandler;
    }

    public void addInterceptStrategy(InterceptStrategy strategy) {
        interceptors.add(strategy);
    }

    public void addInterceptStrategies(List<InterceptStrategy> strategies) {
        interceptors.addAll(strategies);
    }

    public List<InterceptStrategy> getInterceptStrategies() {
        return interceptors;
    }

    public ProcessorDefinition getProcessorDefinition() {
        return definition;
    }

    @Override
    protected void doStart() throws Exception {
        ServiceHelper.startServices(errorHandler, output);
    }

    @Override
    protected void doStop() throws Exception {
        ServiceHelper.stopServices(output, errorHandler);
    }

    public void initChannel(ProcessorDefinition outputDefinition, RouteContext routeContext) throws Exception {
        this.definition = outputDefinition;
        this.routeContext = routeContext;
        this.camelContext = routeContext.getCamelContext();

        Processor target = nextProcessor;
        Processor next;

        // first wrap the output with the managed strategy if any
        InterceptStrategy managed = routeContext.getManagedInterceptStrategy();
        if (managed != null) {
            next = target == nextProcessor ? null : nextProcessor;
            target = managed.wrapProcessorInInterceptors(routeContext.getCamelContext(), outputDefinition, target, next);
        }

        // then wrap the output with the tracer
        TraceInterceptor trace = (TraceInterceptor) getOrCreateTracer().wrapProcessorInInterceptors(routeContext.getCamelContext(), outputDefinition, target, null);
        // trace interceptor need to have a reference to route context so we at runtime can enable/disable tracing on-the-fly
        trace.setRouteContext(routeContext);
        target = trace;

        // wrap the output with the configured interceptors
        for (InterceptStrategy strategy : interceptors) {
            next = target == nextProcessor ? null : nextProcessor;
            // skip tracer as we did the specially beforehand and it could potentially be added as an interceptor strategy
            if (strategy instanceof Tracer) {
                continue;
            }
            target = strategy.wrapProcessorInInterceptors(routeContext.getCamelContext(), outputDefinition, target, next);
        }

        // sets the delegate to our wrapped output
        output = target;
    }

    private InterceptStrategy getOrCreateTracer() {
        InterceptStrategy tracer = Tracer.getTracer(camelContext);
        if (tracer == null) {
            // lookup in registry
            Map<String, Tracer> map = camelContext.getRegistry().lookupByType(Tracer.class);
            if (map.size() == 1) {
                tracer = map.values().iterator().next();
            } else {
                // fallback to use the default tracer
                tracer = camelContext.getDefaultTracer();
            }
        }

        // which we must manage as well
        for (LifecycleStrategy strategy : camelContext.getLifecycleStrategies()) {
            if (tracer instanceof Service) {
                strategy.onServiceAdd(camelContext, (Service) tracer, null);
            }
        }

        return tracer;
    }

    public void process(Exchange exchange) throws Exception {
        Processor processor = getOutput();
        if (processor != null && continueProcessing(exchange)) {
            processor.process(exchange);
        }
    }

    /**
     * Strategy to determine if we should continue processing the {@link Exchange}.
     */
    protected boolean continueProcessing(Exchange exchange) {
        Object stop = exchange.getProperty(Exchange.ROUTE_STOP);
        if (stop != null) {
            boolean doStop = exchange.getContext().getTypeConverter().convertTo(Boolean.class, stop);
            if (doStop) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exchange is marked to stop routing: " + exchange);
                }
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        // just output the next processor as all the interceptors and error handler is just too verbose
        return "Channel[" + nextProcessor + "]";
    }

}
