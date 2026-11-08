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
package org.apache.camel.sanitizer;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.DelegateProcessor;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointAware;
import org.apache.camel.Processor;
import org.apache.camel.Route;
import org.apache.camel.model.BeanDefinition;
import org.apache.camel.model.Model;
import org.apache.camel.model.ProcessDefinition;
import org.apache.camel.model.ProcessorDefinition;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.IdAware;
import org.apache.camel.support.AsyncProcessorConverterHelper;
import org.apache.camel.support.processor.DelegateAsyncProcessor;
import org.apache.camel.support.processor.DelegateSyncProcessor;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The exchange sanitizer of one {@link CamelContext}: it holds the violations reported for it and writes its report. It
 * is installed by {@link SanitizerContextServicePlugin} and is available with {@link #get(CamelContext)}.
 *
 * @since 4.24
 */
public final class Sanitizer {

    /**
     * The most violations kept for {@link #getViolations()} and the report, and waiting in a test session; they are all
     * counted, and charged to the test session.
     */
    static final int MAX_KEPT = 1000;

    private static final Logger LOG = LoggerFactory.getLogger(Sanitizer.class);

    private static final StackWalker WALKER = StackWalker.getInstance();

    private final CamelContext camelContext;
    private final int stackDepth;
    private final SanitizerTestSession session;
    private final LongAdder calls = new LongAdder();
    private final Map<Violation, LongAdder> counts = new EnumMap<>(Violation.class);
    private final ConcurrentLinkedQueue<SanitizerViolation> violations = new ConcurrentLinkedQueue<>();
    private final AtomicInteger kept = new AtomicInteger();
    private final AtomicBoolean overflowLogged = new AtomicBoolean();
    // the class of the processor of a user, by "routeId|stepId"
    private final Map<String, String> userProcessors = new ConcurrentHashMap<>();
    // what a processor threw and was reported, on this thread since its last step call: thrown on through an outer
    // step, it is not reported again (weak, so a thread of a pool does not keep it)
    private final ThreadLocal<WeakReference<Throwable>> lastReportedThrow = new ThreadLocal<>();

    Sanitizer(CamelContext camelContext, int stackDepth, SanitizerTestSession session) {
        this.camelContext = camelContext;
        this.stackDepth = stackDepth;
        this.session = session;
        for (Violation v : Violation.values()) {
            counts.put(v, new LongAdder());
        }
    }

    /**
     * The sanitizer of the context, or {@code null} if it is not installed in it.
     */
    public static Sanitizer get(CamelContext camelContext) {
        return camelContext.getCamelContextExtension().getContextPlugin(Sanitizer.class);
    }

    /**
     * The violations reported so far (at most the first 1000; see {@link #getViolationCount(Violation)}).
     */
    public List<SanitizerViolation> getViolations() {
        return new ArrayList<>(violations);
    }

    /**
     * How many violations of this kind were reported.
     */
    public long getViolationCount(Violation violation) {
        return counts.get(violation).sum();
    }

    /**
     * How many calls of the processors of steps were checked.
     */
    public long getCallCount() {
        return calls.sum();
    }

    /**
     * The test session this context is tied to, or {@code null}.
     */
    SanitizerTestSession getSession() {
        return session;
    }

    int getStackDepth() {
        return stackDepth;
    }

    void called() {
        calls.increment();
        if (lastReportedThrow.get() != null) {
            // a new call on this thread: what it throws is a new throw, also when it is the same instance again
            lastReportedThrow.remove();
        }
    }

    /**
     * The name and the stack of the current thread, from the caller of the sanitizer and of the step listener wrapper
     * of camel-core.
     */
    Trace trace() {
        String thread = Thread.currentThread().getName();
        if (stackDepth <= 0) {
            return new Trace(thread, null);
        }
        StackTraceElement[] frames = WALKER.walk(s -> s
                .dropWhile(f -> isOwnFrame(f.getClassName()))
                .limit(stackDepth)
                .map(StackWalker.StackFrame::toStackTraceElement)
                .toArray(StackTraceElement[]::new));
        return new Trace(thread, frames);
    }

    /**
     * The name of the current thread and the stack of what was thrown.
     */
    Trace trace(Throwable thrown) {
        String thread = Thread.currentThread().getName();
        if (stackDepth <= 0) {
            return new Trace(thread, null);
        }
        StackTraceElement[] all = thrown.getStackTrace();
        StackTraceElement[] frames = new StackTraceElement[Math.min(stackDepth, all.length)];
        System.arraycopy(all, 0, frames, 0, frames.length);
        return new Trace(thread, frames);
    }

    private static boolean isOwnFrame(String className) {
        return className.equals(Sanitizer.class.getName())
                || className.startsWith(SanitizerStepListener.class.getName())
                // the wrapper of camel-core that calls the step listeners (package-private, so named here: a rename of
                // org.apache.camel.impl.engine.StepListenerProcessor must be followed here)
                || className.startsWith("org.apache.camel.impl.engine.StepListenerProcessor");
    }

    /**
     * Whether what a processor threw is reported now: not when it is what this thread reported since its last step
     * call, which is the throw of an inner step thrown on through an outer step.
     */
    boolean firstReportOf(Throwable thrown) {
        WeakReference<Throwable> last = lastReportedThrow.get();
        if (last != null && last.get() == thrown) {
            return false;
        }
        lastReportedThrow.set(new WeakReference<>(thrown));
        return true;
    }

    /**
     * Reports a violation: counts it, logs it at WARN and charges it to the test session.
     */
    void record(
            Violation violation, String exchangeId, String routeId, String stepId, String details) {
        record(violation, exchangeId, routeId, stepId, details, violation.getFix());
    }

    /**
     * Reports a violation with its own usual fix. The first 1000 are kept for the report and logged; the others are
     * counted and still charged to the test session, so a test that breaks the rule fails also when a long-lived
     * context (such as a cached test context) passed the bound before it.
     */
    void record(
            Violation violation, String exchangeId, String routeId, String stepId, String details, String fix) {
        counts.get(violation).increment();
        boolean keep = kept.incrementAndGet() <= MAX_KEPT;
        if (!keep && overflowLogged.compareAndSet(false, true)) {
            LOG.warn("Exchange sanitizer: more than {} violations in CamelContext {}; the others are counted and charged"
                     + " to the test, but not logged or listed in the report",
                    MAX_KEPT, camelContext.getName());
        }
        // the text is built only when the violation is kept here or by a test session: past the bound usually by
        // neither, but it is still charged, so a test session that has room keeps it
        Supplier<SanitizerViolation> v = new Supplier<>() {
            private SanitizerViolation built;

            @Override
            public SanitizerViolation get() {
                if (built == null) {
                    built = violationOf(violation, exchangeId, routeId, stepId, details, fix);
                }
                return built;
            }
        };
        if (keep) {
            violations.add(v.get());
            LOG.warn("{}", v.get().getText());
        }
        if (session != null) {
            session.charge(v);
        } else {
            SanitizerTestSession.chargeUntied(v);
        }
    }

    private SanitizerViolation violationOf(
            Violation violation, String exchangeId, String routeId, String stepId, String details, String fix) {
        String processor = processorOf(routeId, stepId);
        StringBuilder sb = new StringBuilder(512);
        sb.append("Exchange sanitizer: ").append(violation.getTitle()).append(" (R1)\n");
        sb.append("  exchange ").append(exchangeId).append(", route ").append(routeId).append(", step ").append(stepId);
        sb.append(", processor ").append(processor != null ? processor : "(not found)");
        String endpoint = endpointOf(routeId, stepId);
        if (endpoint != null) {
            sb.append(", endpoint ").append(endpoint);
        }
        sb.append('\n');
        sb.append(details);
        sb.append("  The callback of process(exchange, callback) must be called exactly once, and process must not throw"
                  + " (see AsyncProcessor).\n");
        sb.append("  Usual fix: ").append(fix).append('.');
        return new SanitizerViolation(
                violation, camelContext.getName(), exchangeId, routeId, stepId, processor, sb.toString());
    }

    /**
     * Remembers the classes of the processors of users in the routes, by step, when the routes are added: a processor
     * of a user does not know its step id, so it is found through the definition of the step, which is read here and
     * not when a violation is reported (reading the model then could wait for a route that is being removed).
     */
    void routesAdded(Collection<Route> routes) {
        try {
            Model model = camelContext.getCamelContextExtension().getContextPlugin(Model.class);
            if (model == null) {
                return;
            }
            for (Route route : routes) {
                RouteDefinition definition = model.getRouteDefinition(route.getRouteId());
                if (definition != null) {
                    collect(route.getRouteId(), definition.getOutputs());
                }
            }
        } catch (RuntimeException e) {
            LOG.debug("Cannot read the definitions of the routes", e);
        }
    }

    void routesRemoved(Collection<Route> routes) {
        for (Route route : routes) {
            String prefix = route.getRouteId() + "|";
            userProcessors.keySet().removeIf(k -> k.startsWith(prefix));
        }
    }

    private void collect(String routeId, List<ProcessorDefinition<?>> outputs) {
        for (ProcessorDefinition<?> output : outputs) {
            String name = output.getId() != null ? userClassOf(output) : null;
            if (name != null) {
                userProcessors.put(routeId + "|" + output.getId(), name);
            }
            collect(routeId, output.getOutputs());
        }
    }

    /**
     * The class of the code of a user that a step calls: the processor of {@code process}, or the bean of {@code bean}
     * (not Camel's BeanProcessor that calls it).
     */
    private String userClassOf(ProcessorDefinition<?> output) {
        if (output instanceof ProcessDefinition process) {
            Object processor = process.getProcessor();
            if (processor == null && process.getRef() != null) {
                // a processor given by reference (process(ref), and the ref of XML and YAML routes)
                processor = lookup(process.getRef());
            }
            if (processor instanceof Processor p) {
                return unwrap(p).getClass().getName();
            }
            return processor != null ? processor.getClass().getName() : null;
        } else if (output instanceof BeanDefinition bean) {
            return beanClassOf(bean);
        }
        return null;
    }

    private String beanClassOf(BeanDefinition bean) {
        if (bean.getBean() != null) {
            return bean.getBean().getClass().getName();
        } else if (bean.getBeanClass() != null) {
            return bean.getBeanClass().getName();
        } else if (bean.getBeanType() != null) {
            return bean.getBeanType().startsWith("#class:") ? bean.getBeanType().substring(7) : bean.getBeanType();
        } else if (bean.getRef() != null) {
            Object found = lookup(bean.getRef());
            return found != null ? found.getClass().getName() : null;
        }
        return null;
    }

    private Object lookup(String ref) {
        String name = ref.startsWith("#bean:") ? ref.substring(6) : ref.startsWith("#") ? ref.substring(1) : ref;
        return camelContext.getRegistry().lookupByName(name);
    }

    /**
     * The class name of the processor of the step, looking through the delegates Camel puts around a processor (such as
     * the one that {@code process(...)} uses for an {@link org.apache.camel.AsyncProcessor} that is not a
     * {@link org.apache.camel.Service}). It is looked up when a violation is reported, so a step costs nothing for it.
     */
    String processorOf(String routeId, String stepId) {
        String user = userProcessors.get(routeId + "|" + stepId);
        if (user != null) {
            return user;
        }
        Processor p = camelProcessorOf(routeId, stepId);
        return p != null ? unwrap(p).getClass().getName() : null;
    }

    /**
     * The endpoint a step sends to (such as {@code to}), with the URI of the endpoint (sanitized) and its class, as the
     * producer that breaks the rule is behind it.
     */
    String endpointOf(String routeId, String stepId) {
        Processor p = camelProcessorOf(routeId, stepId);
        if (p instanceof EndpointAware aware && aware.getEndpoint() != null) {
            Endpoint endpoint = aware.getEndpoint();
            return URISupport.sanitizeUri(endpoint.getEndpointUri()) + " (" + endpoint.getClass().getName() + ")";
        }
        return null;
    }

    /**
     * The processor of Camel of the step: the processors of Camel know their step id ({@link IdAware}).
     */
    private Processor camelProcessorOf(String routeId, String stepId) {
        try {
            Route route = camelContext.getRoute(routeId);
            if (route != null) {
                for (Processor p : route.filter(stepId)) {
                    if (p instanceof IdAware idAware && stepId.equals(idAware.getId())) {
                        return p;
                    }
                }
            }
        } catch (RuntimeException e) {
            LOG.debug("Cannot find the processor of step {} of route {}", stepId, routeId, e);
        }
        return null;
    }

    static Processor unwrap(Processor processor) {
        Processor p = processor;
        while ((p.getClass() == DelegateAsyncProcessor.class || p.getClass() == DelegateSyncProcessor.class)
                && ((DelegateProcessor) p).getProcessor() != null) {
            p = ((DelegateProcessor) p).getProcessor();
        }
        return p;
    }

    /**
     * The report: the counts per kind and the violations kept.
     */
    public String report() {
        StringBuilder sb = counts();
        for (SanitizerViolation v : violations) {
            sb.append("\n\n").append(v.getText());
        }
        return sb.toString();
    }

    /**
     * The counts per kind and one line per violation kept (the full reports were logged when they happened).
     */
    String summary() {
        StringBuilder sb = counts();
        for (SanitizerViolation v : violations) {
            sb.append("\n  ").append(v.getKind().getTitle()).append(": exchange ").append(v.getExchangeId())
                    .append(", route ").append(v.getRouteId()).append(", step ").append(v.getStepId())
                    .append(", processor ").append(v.getProcessor());
        }
        return sb.toString();
    }

    private StringBuilder counts() {
        StringBuilder sb = new StringBuilder(256);
        sb.append("Exchange sanitizer report of CamelContext ").append(camelContext.getName()).append(": ");
        sb.append(getCallCount()).append(" calls of steps checked");
        long total = 0;
        for (Violation v : Violation.values()) {
            total += getViolationCount(v);
        }
        if (total == 0) {
            return sb.append(", no violations");
        }
        sb.append(", ").append(total).append(" violations");
        for (Violation v : Violation.values()) {
            long n = getViolationCount(v);
            if (n > 0) {
                sb.append("\n  ").append(v.getTitle()).append(": ").append(n);
            }
        }
        if (total > MAX_KEPT) {
            sb.append("\n  (the first ").append(MAX_KEPT).append(" are listed)");
        }
        return sb;
    }

    boolean hasViolations() {
        return kept.get() > 0;
    }

    /**
     * A thread name and its stack, kept for a report.
     */
    static final class Trace {

        /**
         * The methods of Camel that call a plain {@link Processor} and then its callback in {@code finally}, catching
         * only exceptions: an {@link Error} of the processor goes through after the callback (by name, as the bridge is
         * a private nested class, binary name with {@code $}, and the bean one is in camel-bean; a rename must be
         * followed here, which SanitizerUserCodeTest checks).
         */
        static final Set<String> SYNC_ADAPTERS = Set.of(
                DelegateSyncProcessor.class.getName() + ".process",
                AsyncProcessorConverterHelper.class.getName() + "$ProcessorToAsyncProcessorBridge.process",
                "org.apache.camel.component.bean.AbstractBeanProcessor.useCustomAdapter");

        private final String thread;
        private final StackTraceElement[] frames;

        Trace(String thread, StackTraceElement[] frames) {
            this.thread = thread;
            this.frames = frames;
        }

        /**
         * Whether the callback was called by an adapter Camel puts around a plain {@link Processor}, and not by the
         * code of the processor: the first frame that is not a callback (a {@code done} method or a lambda of one) is
         * one of {@link #SYNC_ADAPTERS}. Unknown (false) without stacks ({@code stackDepth=0}).
         */
        boolean calledBySyncAdapter() {
            if (frames == null) {
                return false;
            }
            for (StackTraceElement f : frames) {
                String method = f.getMethodName();
                if ("done".equals(method) || method.startsWith("lambda$")) {
                    continue;
                }
                return SYNC_ADAPTERS.contains(f.getClassName() + "." + method);
            }
            return false;
        }

        void appendTo(StringBuilder sb, String what) {
            sb.append("  ").append(what).append(": thread ").append(thread).append('\n');
            if (frames != null) {
                for (StackTraceElement f : frames) {
                    sb.append("      at ").append(f).append('\n');
                }
            }
        }
    }
}
