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
package org.apache.camel.impl.engine;

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
import org.apache.camel.impl.debugger.BacklogDebugger;
import org.apache.camel.impl.debugger.BacklogTracer;
import org.apache.camel.spi.Debugger;
import org.apache.camel.spi.ErrorHandlerRedeliveryCustomizer;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.spi.ManagementInterceptStrategy;
import org.apache.camel.spi.MessageHistoryFactory;
import org.apache.camel.spi.Tracer;
import org.apache.camel.spi.WrapAwareProcessor;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DefaultChannel is the default {@link Channel}.
 * <p/>
 * The current implementation is just a composite containing the interceptors and error handler that beforehand was
 * added to the route graph directly. <br/>
 * With this {@link Channel} we can in the future implement better strategies for routing the {@link Exchange} in the
 * route graph, as we have a {@link Channel} between each and every node in the graph.
 */
public class DefaultChannel extends CamelInternalProcessor implements Channel {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultChannel.class);

    private Processor errorHandler;
    // the next processor (non wrapped)
    private Processor nextProcessor;
    // the real output to invoke that has been wrapped
    private Processor output;
    private ManagementInterceptStrategy.InstrumentationProcessor<?> instrumentationProcessor;
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

    @Override
    public void setErrorHandler(Processor errorHandler) {
        this.errorHandler = errorHandler;
    }

    @Override
    public Processor getErrorHandler() {
        return errorHandler;
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

    @Override
    public void initChannel(
            Route route,
            NamedNode definition,
            NamedNode childDefinition,
            List<InterceptStrategy> interceptors,
            Processor nextProcessor,
            NamedRoute routeDefinition,
            boolean first)
            throws Exception {
        this.route = route;
        this.nextProcessor = nextProcessor;

        // init CamelContextAware as early as possible on nextProcessor
        CamelContextAware.trySetCamelContext(nextProcessor, camelContext);

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
            final Debugger customDebugger = camelContext.getDebugger();
            if (customDebugger != null) {
                // use custom debugger
                addAdvice(new DebuggerAdvice(customDebugger, nextProcessor, targetOutputDef));
            }
            BacklogDebugger debugger = getBacklogDebugger(camelContext, customDebugger == null);
            if (debugger != null) {
                // use backlog debugger
                camelContext.addService(debugger);
                addAdvice(new BacklogDebuggerAdvice(debugger, nextProcessor, targetOutputDef));
            }
        }

        if (camelContext.isBacklogTracingStandby() || route.isBacklogTracing()) {
            // add jmx backlog tracer
            BacklogTracer backlogTracer = getOrCreateBacklogTracer(camelContext);
            addAdvice(new BacklogTracerAdvice(camelContext, backlogTracer, targetOutputDef, routeDefinition, first));
        }
        if (route.isTracing() || camelContext.isTracingStandby()) {
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
                LOG.warn("Interceptor: {} at: {} does not return an AsyncProcessor instance."
                         + " This causes the asynchronous routing engine to not work as optimal as possible."
                         + " See more details at the InterceptStrategy javadoc."
                         + " Camel will use a bridge to adapt the interceptor to the asynchronous routing engine,"
                         + " but its not the most optimal solution. Please consider changing your interceptor to comply.",
                        strategy, definition);
            }
            if (!(wrapped instanceof WrapAwareProcessor)) {
                // wrap the target so it becomes a service and we can manage its lifecycle
                wrapped = PluginHelper.getInternalProcessorFactory(camelContext)
                        .createWrapProcessor(wrapped, target);
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

    @Override
    public void postInitChannel() throws Exception {
        // if jmx was enabled for the processor then either add as advice or wrap and change the processor
        // on the error handler. See more details in the class javadoc of InstrumentationProcessor
        if (instrumentationProcessor != null) {
            boolean redeliveryPossible = false;
            if (errorHandler instanceof ErrorHandlerRedeliveryCustomizer) {
                ErrorHandlerRedeliveryCustomizer erh = (ErrorHandlerRedeliveryCustomizer) errorHandler;
                redeliveryPossible = erh.determineIfRedeliveryIsEnabled();
                if (redeliveryPossible) {
                    // okay we can redeliver then we need to change the output in the error handler
                    // to use us which we then wrap the call so we can capture before/after for redeliveries as well
                    Processor currentOutput = erh.getOutput();
                    instrumentationProcessor.setProcessor(currentOutput);
                    erh.changeOutput(instrumentationProcessor);
                }
            }
            if (!redeliveryPossible) {
                // optimise to use advice as we cannot redeliver
                addAdvice(CamelInternalProcessor.wrap(instrumentationProcessor));
            }
        }
    }

    private static BacklogTracer getOrCreateBacklogTracer(CamelContext camelContext) {
        BacklogTracer tracer = null;
        if (camelContext.getRegistry() != null) {
            // lookup in registry
            Map<String, BacklogTracer> map = camelContext.getRegistry().findByTypeWithName(BacklogTracer.class);
            if (map.size() == 1) {
                tracer = map.values().iterator().next();
            }
        }
        if (tracer == null) {
            tracer = camelContext.getCamelContextExtension().getContextPlugin(BacklogTracer.class);
        }
        if (tracer == null) {
            tracer = BacklogTracer.createTracer(camelContext);
            tracer.setEnabled(camelContext.isBacklogTracing() != null && camelContext.isBacklogTracing());
            tracer.setStandby(camelContext.isBacklogTracingStandby());
            // enable both rest/templates if templates is enabled (we only want 1 public option)
            boolean restOrTemplates = camelContext.isBacklogTracingTemplates();
            tracer.setTraceTemplates(restOrTemplates);
            tracer.setTraceRests(restOrTemplates);
            camelContext.getCamelContextExtension().addContextPlugin(BacklogTracer.class, tracer);
        }
        return tracer;
    }

    /**
     * @param  camelContext   the camel context from which the {@link BacklogDebugger} should be found.
     * @param  createIfAbsent indicates whether a {@link BacklogDebugger} should be created if it cannot be found
     * @return                the instance of {@link BacklogDebugger} that could be found in the context or that was
     *                        created if {@code createIfAbsent} is set to {@code true}, {@code null} otherwise.
     */
    private static BacklogDebugger getBacklogDebugger(CamelContext camelContext, boolean createIfAbsent) {
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
        if (debugger == null && createIfAbsent) {
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
