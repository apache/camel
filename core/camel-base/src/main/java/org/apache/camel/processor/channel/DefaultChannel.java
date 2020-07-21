/*
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
package org.apache.camel.processor.channel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Channel;
import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.NamedRoute;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.processor.CamelInternalProcessor;
import org.apache.camel.processor.WrapProcessor;
import org.apache.camel.processor.errorhandler.RedeliveryErrorHandler;
import org.apache.camel.processor.interceptor.BacklogDebugger;
import org.apache.camel.processor.interceptor.BacklogTracer;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.ManagementInterceptStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.Tracer;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.service.ServiceHelper;
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
 */
public class DefaultChannel extends CamelInternalProcessor implements Channel {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultChannel.class);

    private Processor errorHandler;
    // the next processor (non wrapped)
    private Processor nextProcessor;
    // the real output to invoke that has been wrapped
    private Processor output;
    private NamedNode definition;
    private ManagementInterceptStrategy.InstrumentationProcessor<?> instrumentationProcessor;
    private CamelContext camelContext;
    private Route route;

    public DefaultChannel(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    public Processor getOutput() {
        // the errorHandler is already decorated with interceptors
        // so it contain the entire chain of processors, so we can safely use it directly as output
        // if no error handler provided we use the output
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
        List<Processor> answer = new ArrayList<>(1);
        answer.add(nextProcessor);
        return answer;
    }

    public void setOutput(Processor output) {
        this.output = output;
    }

    @Override
    public Processor getNextProcessor() {
        return nextProcessor;
    }

    /**
     * Sets the {@link org.apache.camel.processor.ErrorHandler} this Channel uses.
     *
     * @param errorHandler the error handler
     */
    public void setErrorHandler(Processor errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public Processor getErrorHandler() {
        return errorHandler;
    }

    @Override
    public NamedNode getProcessorDefinition() {
        return definition;
    }

    public void clearModelReferences() {
        this.definition = null;
    }

    @Override
    public Route getRoute() {
        return route;
    }

    @Override
    protected void doStart() throws Exception {
        // do not call super as we want to be in control here of the lifecycle

        // the output has now been created, so assign the output as the processor
        setProcessor(getOutput());
        ServiceHelper.startService(errorHandler, output);
    }

    @Override
    protected void doStop() throws Exception {
        // do not call super as we want to be in control here of the lifecycle

        // only stop services if not context scoped (as context scoped is reused by others)
        ServiceHelper.stopService(output, errorHandler);
    }

    @Override
    protected void doShutdown() throws Exception {
        // do not call super as we want to be in control here of the lifecycle

        ServiceHelper.stopAndShutdownServices(output, errorHandler);
    }

    /**
     * Initializes the channel.
     * If the initialized output definition contained outputs (children) then
     * the childDefinition will be set so we can leverage fine grained tracing
     *
     * @param route      the route context
     * @param definition        the route definition the {@link Channel} represents
     * @param childDefinition   the child definition
     * @throws Exception is thrown if some error occurred
     */
    public void initChannel(Route route,
                            NamedNode definition,
                            NamedNode childDefinition,
                            List<InterceptStrategy> interceptors,
                            Processor nextProcessor,
                            NamedRoute routeDefinition,
                            boolean first) throws Exception {
        this.route = route;
        this.definition = definition;
        this.camelContext = route.getCamelContext();
        this.nextProcessor = nextProcessor;

        // init CamelContextAware as early as possible on nextProcessor
        if (nextProcessor instanceof CamelContextAware) {
            ((CamelContextAware) nextProcessor).setCamelContext(camelContext);
        }

        // the definition to wrap should be the fine grained,
        // so if a child is set then use it, if not then its the original output used
        NamedNode targetOutputDef = childDefinition != null ? childDefinition : definition;
        LOG.trace("Initialize channel for target: {}", targetOutputDef);

        // setup instrumentation processor for management (jmx)
        // this is later used in postInitChannel as we need to setup the error handler later as well
        ManagementInterceptStrategy managed = route.getManagementInterceptStrategy();
        if (managed != null) {
            instrumentationProcessor = managed.createProcessor(targetOutputDef, nextProcessor);
        }

        if (route.isMessageHistory()) {
            // add message history advice
            MessageHistoryFactory factory = camelContext.getMessageHistoryFactory();
            addAdvice(new MessageHistoryAdvice(factory, targetOutputDef));
        }
        // add advice that keeps track of which node is processing
        addAdvice(new NodeHistoryAdvice(targetOutputDef));

        // then wrap the output with the tracer and debugger (debugger first,
        // as we do not want regular tracer to trace the debugger)
        if (route.isDebugging()) {
            if (camelContext.getDebugger() != null) {
                // use custom debugger
                Debugger debugger = camelContext.getDebugger();
                addAdvice(new DebuggerAdvice(debugger, nextProcessor, targetOutputDef));
            } else {
                // use backlog debugger
                BacklogDebugger debugger = getOrCreateBacklogDebugger();
                camelContext.addService(debugger);
                addAdvice(new BacklogDebuggerAdvice(debugger, nextProcessor, targetOutputDef));
            }
        }

        if (route.isBacklogTracing()) {
            // add jmx backlog tracer
            BacklogTracer backlogTracer = getOrCreateBacklogTracer();
            addAdvice(new BacklogTracerAdvice(backlogTracer, targetOutputDef, routeDefinition, first));
        }
        if (route.isTracing()) {
            // add logger tracer
            Tracer tracer = camelContext.getTracer();
            addAdvice(new TracingAdvice(tracer, targetOutputDef, routeDefinition, first));
        }

        // sort interceptors according to ordered
        interceptors.sort(OrderedComparator.get());
        // reverse list so the first will be wrapped last, as it would then be first being invoked
        Collections.reverse(interceptors);
        // wrap the output with the configured interceptors
        Processor target = nextProcessor;
        for (InterceptStrategy strategy : interceptors) {
            Processor next = target == nextProcessor ? null : nextProcessor;
            // use the fine grained definition (eg the child if available). Its always possible to get back to the parent
            Processor wrapped = strategy.wrapProcessorInInterceptors(route.getCamelContext(), targetOutputDef, target, next);
            if (!(wrapped instanceof AsyncProcessor)) {
                LOG.warn("Interceptor: " + strategy + " at: " + definition + " does not return an AsyncProcessor instance."
                        + " This causes the asynchronous routing engine to not work as optimal as possible."
                        + " See more details at the InterceptStrategy javadoc."
                        + " Camel will use a bridge to adapt the interceptor to the asynchronous routing engine,"
                        + " but its not the most optimal solution. Please consider changing your interceptor to comply.");
            }
            if (!(wrapped instanceof WrapProcessor)) {
                // wrap the target so it becomes a service and we can manage its lifecycle
                wrapped = new WrapProcessor(wrapped, target);
            }
            target = wrapped;
        }

        if (route.isStreamCaching()) {
            addAdvice(new StreamCachingAdvice(camelContext.getStreamCachingStrategy()));
        }

        if (route.getDelayer() != null && route.getDelayer() > 0) {
            addAdvice(new DelayerAdvice(route.getDelayer()));
        }

        // sets the delegate to our wrapped output
        output = target;
    }

    /**
     * Post initializes the channel.
     *
     * @throws Exception is thrown if some error occurred
     */
    public void postInitChannel() throws Exception {
        // if jmx was enabled for the processor then either add as advice or wrap and change the processor
        // on the error handler. See more details in the class javadoc of InstrumentationProcessor
        if (instrumentationProcessor != null) {
            boolean redeliveryPossible = false;
            if (errorHandler instanceof RedeliveryErrorHandler) {
                redeliveryPossible = ((RedeliveryErrorHandler) errorHandler).determineIfRedeliveryIsEnabled();
                if (redeliveryPossible) {
                    // okay we can redeliver then we need to change the output in the error handler
                    // to use us which we then wrap the call so we can capture before/after for redeliveries as well
                    Processor currentOutput = ((RedeliveryErrorHandler) errorHandler).getOutput();
                    instrumentationProcessor.setProcessor(currentOutput);
                    ((RedeliveryErrorHandler) errorHandler).changeOutput(instrumentationProcessor);
                }
            }
            if (!redeliveryPossible) {
                // optimise to use advice as we cannot redeliver
                addAdvice(CamelInternalProcessor.wrap(instrumentationProcessor));
            }
        }
    }

    private BacklogTracer getOrCreateBacklogTracer() {
        BacklogTracer tracer = null;
        if (camelContext.getRegistry() != null) {
            // lookup in registry
            Map<String, BacklogTracer> map = camelContext.getRegistry().findByTypeWithName(BacklogTracer.class);
            if (map.size() == 1) {
                tracer = map.values().iterator().next();
            }
        }
        if (tracer == null) {
            tracer = camelContext.getExtension(BacklogTracer.class);
        }
        if (tracer == null) {
            tracer = BacklogTracer.createTracer(camelContext);
            camelContext.setExtension(BacklogTracer.class, tracer);
        }
        return tracer;
    }

    private BacklogDebugger getOrCreateBacklogDebugger() {
        BacklogDebugger debugger = null;
        if (camelContext.getRegistry() != null) {
            // lookup in registry
            Map<String, BacklogDebugger> map = camelContext.getRegistry().findByTypeWithName(BacklogDebugger.class);
            if (map.size() == 1) {
                debugger = map.values().iterator().next();
            }
        }
        if (debugger == null) {
            debugger = camelContext.hasService(BacklogDebugger.class);
        }
        if (debugger == null) {
            // fallback to use the default debugger
            debugger = BacklogDebugger.createDebugger(camelContext);
        }
        return debugger;
    }

    @Override
    public String toString() {
        // just output the next processor as all the interceptors and error handler is just too verbose
        return "Channel[" + nextProcessor + "]";
    }

}
