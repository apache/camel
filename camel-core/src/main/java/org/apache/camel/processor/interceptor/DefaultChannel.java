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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.management.InstrumentationInterceptStrategy;
import org.apache.camel.management.InstrumentationProcessor;
import org.apache.camel.model.ModelChannel;
import org.apache.camel.model.OnCompletionDefinition;
import org.apache.camel.model.OnExceptionDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.ProcessorDefinitionHelper;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.model.RouteDefinitionHelper;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.InterceptorToAsyncProcessorBridge;
import org.apache.camel.processor.RedeliveryErrorHandler;
import org.apache.camel.processor.WrapProcessor;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.OrderedComparator;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 * @version 
 */
public class DefaultChannel extends CamelInternalProcessor implements ModelChannel {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultChannel.class);

    private final List<InterceptStrategy> interceptors = new ArrayList<InterceptStrategy>();
    private Processor errorHandler;
    // the next processor (non wrapped)
    private Processor nextProcessor;
    // the real output to invoke that has been wrapped
    private Processor output;
    private ProcessorDefinition<?> definition;
    private ProcessorDefinition<?> childDefinition;
    private InstrumentationProcessor instrumentationProcessor;
    private CamelContext camelContext;
    private RouteContext routeContext;

    public void setNextProcessor(Processor next) {
        this.nextProcessor = next;
    }

    public Processor getOutput() {
        // the errorHandler is already decorated with interceptors
        // so it contain the entire chain of processors, so we can safely use it directly as output
        // if no error handler provided we use the output
        // TODO: Camel 3.0 we should determine the output dynamically at runtime instead of having the
        // the error handlers, interceptors, etc. woven in at design time
        return errorHandler != null ? errorHandler : output;
    }

    @Override
    public boolean hasNext() {
        return nextProcessor != null;
    }

    @Override
    public List<Processor> next() {
        if (!hasNext()) {
            return null;
        }
        List<Processor> answer = new ArrayList<Processor>(1);
        answer.add(nextProcessor);
        return answer;
    }

    public void setOutput(Processor output) {
        this.output = output;
    }

    public Processor getNextProcessor() {
        return nextProcessor;
    }

    public boolean hasInterceptorStrategy(Class<?> type) {
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

    public ProcessorDefinition<?> getProcessorDefinition() {
        return definition;
    }

    public void setChildDefinition(ProcessorDefinition<?> childDefinition) {
        this.childDefinition = childDefinition;
    }

    public RouteContext getRouteContext() {
        return routeContext;
    }

    @Override
    protected void doStart() throws Exception {
        // the output has now been created, so assign the output as the processor
        setProcessor(getOutput());
        ServiceHelper.startServices(errorHandler, output);
    }

    @Override
    protected void doStop() throws Exception {
        if (!isContextScoped()) {
            // only stop services if not context scoped (as context scoped is reused by others)
            ServiceHelper.stopServices(output, errorHandler);
        }
    }

    @Override
    protected void doShutdown() throws Exception {
        ServiceHelper.stopAndShutdownServices(output, errorHandler);
    }

    private boolean isContextScoped() {
        if (definition instanceof OnExceptionDefinition) {
            return !((OnExceptionDefinition) definition).isRouteScoped();
        } else if (definition instanceof OnCompletionDefinition) {
            return !((OnCompletionDefinition) definition).isRouteScoped();
        }

        return false;
    }

    @SuppressWarnings("deprecation")
    public void initChannel(ProcessorDefinition<?> outputDefinition, RouteContext routeContext) throws Exception {
        this.routeContext = routeContext;
        this.definition = outputDefinition;
        this.camelContext = routeContext.getCamelContext();

        Processor target = nextProcessor;
        Processor next;

        // init CamelContextAware as early as possible on target
        if (target instanceof CamelContextAware) {
            ((CamelContextAware) target).setCamelContext(camelContext);
        }

        // the definition to wrap should be the fine grained,
        // so if a child is set then use it, if not then its the original output used
        ProcessorDefinition<?> targetOutputDef = childDefinition != null ? childDefinition : outputDefinition;
        LOG.debug("Initialize channel for target: '{}'", targetOutputDef);

        // fix parent/child relationship. This will be the case of the routes has been
        // defined using XML DSL or end user may have manually assembled a route from the model.
        // Background note: parent/child relationship is assembled on-the-fly when using Java DSL (fluent builders)
        // where as when using XML DSL (JAXB) then it fixed after, but if people are using custom interceptors
        // then we need to fix the parent/child relationship beforehand, and thus we can do it here
        // ideally we need the design time route -> runtime route to be a 2-phase pass (scheduled work for Camel 3.0)
        if (childDefinition != null && outputDefinition != childDefinition) {
            childDefinition.setParent(outputDefinition);
        }

        // force the creation of an id
        RouteDefinitionHelper.forceAssignIds(routeContext.getCamelContext(), definition);

        // setup instrumentation processor for management (jmx)
        // this is later used in postInitChannel as we need to setup the error handler later as well
        InterceptStrategy managed = routeContext.getManagedInterceptStrategy();
        if (managed instanceof InstrumentationInterceptStrategy) {
            InstrumentationInterceptStrategy iis = (InstrumentationInterceptStrategy) managed;
            instrumentationProcessor = new InstrumentationProcessor(targetOutputDef.getShortName(), target);
            iis.prepareProcessor(targetOutputDef, target, instrumentationProcessor);
        }

        // then wrap the output with the backlog and tracer (backlog first, as we do not want regular tracer to tracer the backlog)
        InterceptStrategy tracer = getOrCreateBacklogTracer();
        camelContext.addService(tracer);
        if (tracer instanceof BacklogTracer) {
            BacklogTracer backlogTracer = (BacklogTracer) tracer;

            RouteDefinition route = ProcessorDefinitionHelper.getRoute(definition);
            boolean first = false;
            if (route != null && !route.getOutputs().isEmpty()) {
                first = route.getOutputs().get(0) == definition;
            }

            addAdvice(new BacklogTracerAdvice(backlogTracer, targetOutputDef, route, first));

            // add debugger as well so we have both tracing and debugging out of the box
            InterceptStrategy debugger = getOrCreateBacklogDebugger();
            camelContext.addService(debugger);
            if (debugger instanceof BacklogDebugger) {
                BacklogDebugger backlogDebugger = (BacklogDebugger) debugger;
                addAdvice(new BacklogDebuggerAdvice(backlogDebugger, target, targetOutputDef));
            }
        }

        if (routeContext.isMessageHistory()) {
            // add message history advice
            MessageHistoryFactory factory = camelContext.getMessageHistoryFactory();
            addAdvice(new MessageHistoryAdvice(factory, targetOutputDef));
        }

        // the regular tracer is not a task on internalProcessor as this is not really needed
        // end users have to explicit enable the tracer to use it, and then its okay if we wrap
        // the processors (but by default tracer is disabled, and therefore we do not wrap processors)
        tracer = getOrCreateTracer();
        if (tracer != null) {
            camelContext.addService(tracer);
            TraceInterceptor trace = (TraceInterceptor) tracer.wrapProcessorInInterceptors(routeContext.getCamelContext(), targetOutputDef, target, null);
            // trace interceptor need to have a reference to route context so we at runtime can enable/disable tracing on-the-fly
            trace.setRouteContext(routeContext);
            target = trace;
        }

        // sort interceptors according to ordered
        interceptors.sort(OrderedComparator.get());
        // then reverse list so the first will be wrapped last, as it would then be first being invoked
        Collections.reverse(interceptors);
        // wrap the output with the configured interceptors
        for (InterceptStrategy strategy : interceptors) {
            next = target == nextProcessor ? null : nextProcessor;
            // skip tracer as we did the specially beforehand and it could potentially be added as an interceptor strategy
            if (strategy instanceof Tracer) {
                continue;
            }
            // skip stream caching as it must be wrapped as outer most, which we do later
            if (strategy instanceof StreamCaching) {
                continue;
            }
            // use the fine grained definition (eg the child if available). Its always possible to get back to the parent
            Processor wrapped = strategy.wrapProcessorInInterceptors(routeContext.getCamelContext(), targetOutputDef, target, next);
            if (!(wrapped instanceof AsyncProcessor)) {
                LOG.warn("Interceptor: " + strategy + " at: " + outputDefinition + " does not return an AsyncProcessor instance."
                        + " This causes the asynchronous routing engine to not work as optimal as possible."
                        + " See more details at the InterceptStrategy javadoc."
                        + " Camel will use a bridge to adapt the interceptor to the asynchronous routing engine,"
                        + " but its not the most optimal solution. Please consider changing your interceptor to comply.");

                // use a bridge and wrap again which allows us to adapt and leverage the asynchronous routing engine anyway
                // however its not the most optimal solution, but we can still run.
                InterceptorToAsyncProcessorBridge bridge = new InterceptorToAsyncProcessorBridge(target);
                wrapped = strategy.wrapProcessorInInterceptors(routeContext.getCamelContext(), targetOutputDef, bridge, next);
                // Avoid the stack overflow
                if (!wrapped.equals(bridge)) {
                    bridge.setTarget(wrapped);
                } else {
                    // Just skip the wrapped processor
                    bridge.setTarget(null);
                }
                wrapped = bridge;
            }
            if (!(wrapped instanceof WrapProcessor)) {
                // wrap the target so it becomes a service and we can manage its lifecycle
                wrapped = new WrapProcessor(wrapped, target);
            }
            target = wrapped;
        }

        if (routeContext.isStreamCaching()) {
            addAdvice(new StreamCachingAdvice(camelContext.getStreamCachingStrategy()));
        }

        if (routeContext.getDelayer() != null && routeContext.getDelayer() > 0) {
            addAdvice(new DelayerAdvice(routeContext.getDelayer()));
        }

        // sets the delegate to our wrapped output
        output = target;
    }

    @Override
    public void postInitChannel(ProcessorDefinition<?> outputDefinition, RouteContext routeContext) throws Exception {
        // if jmx was enabled for the processor then either add as advice or wrap and change the processor
        // on the error handler. See more details in the class javadoc of InstrumentationProcessor
        if (instrumentationProcessor != null) {
            boolean redeliveryPossible = false;
            if (errorHandler instanceof RedeliveryErrorHandler) {
                redeliveryPossible = ((RedeliveryErrorHandler) errorHandler).determineIfRedeliveryIsEnabled();
                if (redeliveryPossible) {
                    // okay we can redeliver then we need to change the output in the error handler
                    // to use us which we then wrap the call so we can capture before/after for redeliveries as well
                    ((RedeliveryErrorHandler) errorHandler).changeOutput(instrumentationProcessor);
                }
            }
            if (!redeliveryPossible) {
                // optimise to use advice as we cannot redeliver
                addAdvice(instrumentationProcessor);
            }
        }
    }

    private InterceptStrategy getOrCreateTracer() {
        // only use tracer if explicit enabled
        if (camelContext.isTracing() != null && !camelContext.isTracing()) {
            return null;
        }

        InterceptStrategy tracer = Tracer.getTracer(camelContext);
        if (tracer == null) {
            if (camelContext.getRegistry() != null) {
                // lookup in registry
                Map<String, Tracer> map = camelContext.getRegistry().findByTypeWithName(Tracer.class);
                if (map.size() == 1) {
                    tracer = map.values().iterator().next();
                }
            }
            if (tracer == null) {
                // fallback to use the default tracer
                tracer = camelContext.getDefaultTracer();

                // configure and use any trace formatter if any exists
                Map<String, TraceFormatter> formatters = camelContext.getRegistry().findByTypeWithName(TraceFormatter.class);
                if (formatters.size() == 1) {
                    TraceFormatter formatter = formatters.values().iterator().next();
                    if (tracer instanceof Tracer) {
                        ((Tracer) tracer).setFormatter(formatter);
                    }
                }
            }
        }

        return tracer;
    }

    private InterceptStrategy getOrCreateBacklogTracer() {
        InterceptStrategy tracer = BacklogTracer.getBacklogTracer(camelContext);
        if (tracer == null) {
            if (camelContext.getRegistry() != null) {
                // lookup in registry
                Map<String, BacklogTracer> map = camelContext.getRegistry().findByTypeWithName(BacklogTracer.class);
                if (map.size() == 1) {
                    tracer = map.values().iterator().next();
                }
            }
            if (tracer == null) {
                // fallback to use the default tracer
                tracer = camelContext.getDefaultBacklogTracer();
            }
        }

        return tracer;
    }

    private InterceptStrategy getOrCreateBacklogDebugger() {
        InterceptStrategy debugger = BacklogDebugger.getBacklogDebugger(camelContext);
        if (debugger == null) {
            if (camelContext.getRegistry() != null) {
                // lookup in registry
                Map<String, BacklogDebugger> map = camelContext.getRegistry().findByTypeWithName(BacklogDebugger.class);
                if (map.size() == 1) {
                    debugger = map.values().iterator().next();
                }
            }
            if (debugger == null) {
                // fallback to use the default debugger
                debugger = camelContext.getDefaultBacklogDebugger();
            }
        }

        return debugger;
    }

    @Override
    public String toString() {
        // just output the next processor as all the interceptors and error handler is just too verbose
        return "Channel[" + nextProcessor + "]";
    }

}
