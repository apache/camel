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
package org.apache.camel.main;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultModelJAXBContextFactory;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for main implementations to allow starting up a JVM with Camel embedded.
 *
 * @version 
 */
public abstract class MainSupport extends ServiceSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(MainSupport.class);
    protected final List<MainListener> listeners = new ArrayList<MainListener>();
    protected final List<Option> options = new ArrayList<Option>();
    protected final CountDownLatch latch = new CountDownLatch(1);
    protected final AtomicBoolean completed = new AtomicBoolean(false);
    protected long duration = -1;
    protected TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    protected boolean trace;
    protected List<RouteBuilder> routeBuilders = new ArrayList<RouteBuilder>();
    protected String routeBuilderClasses;
    protected final List<CamelContext> camelContexts = new ArrayList<CamelContext>();
    protected ProducerTemplate camelTemplate;

    /**
     * A class for intercepting the hang up signal and do a graceful shutdown of the Camel.
     */
    private static final class HangupInterceptor extends Thread {
        Logger log = LoggerFactory.getLogger(this.getClass());
        MainSupport mainInstance;

        public HangupInterceptor(MainSupport main) {
            mainInstance = main;
        }

        @Override
        public void run() {
            log.info("Received hang up - stopping the main instance.");
            try {
                mainInstance.stop();
            } catch (Exception ex) {
                log.warn("Error during stopping the main instance.", ex);
            }
        }
    }

    protected MainSupport() {
        addOption(new Option("h", "help", "Displays the help screen") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                showOptions();
                completed();
            }
        });
        addOption(new ParameterOption("r", "routers",
                 "Sets the router builder classes which will be loaded while starting the camel context",
                 "routerBuilderClasses") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setRouteBuilderClasses(parameter);
            }
        });
        addOption(new ParameterOption("d", "duration",
                "Sets the time duration that the application will run for, by default in milliseconds. You can use '10s' for 10 seconds etc",
                "duration") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                String value = parameter.toUpperCase(Locale.ENGLISH);
                if (value.endsWith("S")) {
                    value = value.substring(0, value.length() - 1);
                    setTimeUnit(TimeUnit.SECONDS);
                }
                setDuration(Integer.parseInt(value));
            }
        });
        addOption(new Option("t", "trace", "Enables tracing") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                enableTrace();
            }
        });
    }

    /**
     * Runs this process with the given arguments, and will wait until completed, or the JVM terminates.
     */
    public void run() throws Exception {
        if (!completed.get()) {
            // if we have an issue starting then propagate the exception to caller
            beforeStart();
            start();
            try {
                afterStart();
                waitUntilCompleted();
                internalBeforeStop();
                beforeStop();
                stop();
                afterStop();
            } catch (Exception e) {
                // however while running then just log errors
                LOG.error("Failed: " + e, e);
            }
        }
    }

    /**
     * Enables the hangup support. Gracefully stops by calling stop() on a
     * Hangup signal.
     */
    public void enableHangupSupport() {
        HangupInterceptor interceptor = new HangupInterceptor(this);
        Runtime.getRuntime().addShutdownHook(interceptor);
    }

    /**
     * Adds a {@link org.apache.camel.main.MainListener} to receive callbacks when the main is started or stopping
     *
     * @param listener the listener
     */
    public void addMainListener(MainListener listener) {
        listeners.add(listener);
    }

    /**
     * Removes the {@link org.apache.camel.main.MainListener}
     *
     * @param listener the listener
     */
    public void removeMainListener(MainListener listener) {
        listeners.remove(listener);
    }

    /**
     * Callback to run custom logic before CamelContext is being started.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void beforeStart() throws Exception {
        for (MainListener listener : listeners) {
            listener.beforeStart(this);
        }
    }

    /**
     * Callback to run custom logic after CamelContext has been started.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void afterStart() throws Exception {
        for (MainListener listener : listeners) {
            listener.afterStart(this);
        }
    }

    /**
     * Callback to run custom logic before CamelContext is being stopped.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void beforeStop() throws Exception {
        for (MainListener listener : listeners) {
            listener.beforeStop(this);
        }
    }

    /**
     * Callback to run custom logic after CamelContext has been stopped.
     * <p/>
     * It is recommended to use {@link org.apache.camel.main.MainListener} instead.
     */
    protected void afterStop() throws Exception {
        for (MainListener listener : listeners) {
            listener.afterStop(this);
        }
    }

    private void internalBeforeStop() {
        try {
            if (camelTemplate != null) {
                ServiceHelper.stopService(camelTemplate);
                camelTemplate = null;
            }
        } catch (Exception e) {
            LOG.debug("Error stopping camelTemplate due " + e.getMessage() + ". This exception is ignored.", e);
        }
    }

    /**
     * Marks this process as being completed.
     */
    public void completed() {
        completed.set(true);
        latch.countDown();
    }

    /**
     * Displays the command line options.
     */
    public void showOptions() {
        showOptionsHeader();

        for (Option option : options) {
            System.out.println(option.getInformation());
        }
    }

    /**
     * Parses the command line arguments.
     */
    public void parseArguments(String[] arguments) {
        LinkedList<String> args = new LinkedList<String>(Arrays.asList(arguments));

        boolean valid = true;
        while (!args.isEmpty()) {
            String arg = args.removeFirst();

            boolean handled = false;
            for (Option option : options) {
                if (option.processOption(arg, args)) {
                    handled = true;
                    break;
                }
            }
            if (!handled) {
                System.out.println("Unknown option: " + arg);
                System.out.println();
                valid = false;
                break;
            }
        }
        if (!valid) {
            showOptions();
            completed();
        }
    }

    public void addOption(Option option) {
        options.add(option);
    }

    public long getDuration() {
        return duration;
    }

    /**
     * Sets the duration to run the application for in milliseconds until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit duration.
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public void setRouteBuilderClasses(String builders) {
        this.routeBuilderClasses = builders;
    }

    public String getRouteBuilderClasses() {
        return routeBuilderClasses;
    }

    public boolean isTrace() {
        return trace;
    }

    public void enableTrace() {
        this.trace = true;
    }

    protected void doStop() throws Exception {
        // call completed to properly stop as we count down the waiting latch
        completed();
    }

    protected void doStart() throws Exception {
    }

    protected void waitUntilCompleted() {
        while (!completed.get()) {
            try {
                if (duration > 0) {
                    TimeUnit unit = getTimeUnit();
                    LOG.info("Waiting for: " + duration + " " + unit);
                    latch.await(duration, unit);
                    completed.set(true);
                } else {
                    latch.await();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Parses the command line arguments then runs the program.
     */
    public void run(String[] args) throws Exception {
        parseArguments(args);
        run();
    }

    /**
     * Displays the header message for the command line options.
     */
    public void showOptionsHeader() {
        System.out.println("Apache Camel Runner takes the following options");
        System.out.println();
    }

    public List<CamelContext> getCamelContexts() {
        return camelContexts;
    }

    public List<RouteBuilder> getRouteBuilders() {
        return routeBuilders;
    }

    public void setRouteBuilders(List<RouteBuilder> routeBuilders) {
        this.routeBuilders = routeBuilders;
    }

    public List<RouteDefinition> getRouteDefinitions() {
        List<RouteDefinition> answer = new ArrayList<RouteDefinition>();
        for (CamelContext camelContext : camelContexts) {
            answer.addAll(((ModelCamelContext)camelContext).getRouteDefinitions());
        }
        return answer;
    }

    public ProducerTemplate getCamelTemplate() throws Exception {
        if (camelTemplate == null) {
            camelTemplate = findOrCreateCamelTemplate();
        }
        return camelTemplate;
    }

    protected abstract ProducerTemplate findOrCreateCamelTemplate();

    protected abstract Map<String, CamelContext> getCamelContextMap();

    protected void postProcessContext() throws Exception {
        Map<String, CamelContext> map = getCamelContextMap();
        Set<Map.Entry<String, CamelContext>> entries = map.entrySet();
        for (Map.Entry<String, CamelContext> entry : entries) {
            CamelContext camelContext = entry.getValue();
            camelContexts.add(camelContext);
            postProcessCamelContext(camelContext);
        }
    }

    public ModelJAXBContextFactory getModelJAXBContextFactory() {
        return new DefaultModelJAXBContextFactory();
    }

    protected void loadRouteBuilders(CamelContext camelContext) throws Exception {
        if (routeBuilderClasses != null) {
            // get the list of route builder classes
            String[] routeClasses = routeBuilderClasses.split(",");
            for (String routeClass : routeClasses) {
                Class<?> routeClazz = camelContext.getClassResolver().resolveClass(routeClass);
                RouteBuilder builder = (RouteBuilder) routeClazz.newInstance();
                getRouteBuilders().add(builder);
            }
        }
    }

    protected void postProcessCamelContext(CamelContext camelContext) throws Exception {
        if (trace) {
            camelContext.setTracing(true);
        }
        // try to load the route builders from the routeBuilderClasses
        loadRouteBuilders(camelContext);
        for (RouteBuilder routeBuilder : routeBuilders) {
            camelContext.addRoutes(routeBuilder);
        }
        // allow to do configuration before its started
        for (MainListener listener : listeners) {
            listener.configure(camelContext);
        }
    }

    public void addRouteBuilder(RouteBuilder routeBuilder) {
        getRouteBuilders().add(routeBuilder);
    }

    public abstract class Option {
        private String abbreviation;
        private String fullName;
        private String description;

        protected Option(String abbreviation, String fullName, String description) {
            this.abbreviation = "-" + abbreviation;
            this.fullName = "-" + fullName;
            this.description = description;
        }

        public boolean processOption(String arg, LinkedList<String> remainingArgs) {
            if (arg.equalsIgnoreCase(abbreviation) || fullName.startsWith(arg)) {
                doProcess(arg, remainingArgs);
                return true;
            }
            return false;
        }

        public String getAbbreviation() {
            return abbreviation;
        }

        public String getDescription() {
            return description;
        }

        public String getFullName() {
            return fullName;
        }

        public String getInformation() {
            return "  " + getAbbreviation() + " or " + getFullName() + " = " + getDescription();
        }

        protected abstract void doProcess(String arg, LinkedList<String> remainingArgs);
    }

    public abstract class ParameterOption extends Option {
        private String parameterName;

        protected ParameterOption(String abbreviation, String fullName, String description, String parameterName) {
            super(abbreviation, fullName, description);
            this.parameterName = parameterName;
        }

        protected void doProcess(String arg, LinkedList<String> remainingArgs) {
            if (remainingArgs.isEmpty()) {
                System.err.println("Expected fileName for ");
                showOptions();
                completed();
            } else {
                String parameter = remainingArgs.removeFirst();
                doProcess(arg, parameter, remainingArgs);
            }
        }

        public String getInformation() {
            return "  " + getAbbreviation() + " or " + getFullName() + " <" + parameterName + "> = " + getDescription();
        }

        protected abstract void doProcess(String arg, String parameter, LinkedList<String> remainingArgs);
    }
}
