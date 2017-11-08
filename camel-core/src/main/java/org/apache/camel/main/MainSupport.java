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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultModelJAXBContextFactory;
import org.apache.camel.impl.FileWatcherReloadStrategy;
import org.apache.camel.model.RouteDefinition;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.spi.ModelJAXBContextFactory;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.support.ServiceSupport;
import org.apache.camel.util.ServiceHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for main implementations to allow starting up a JVM with Camel embedded.
 *
 * @version 
 */
public abstract class MainSupport extends ServiceSupport {
    protected static final Logger LOG = LoggerFactory.getLogger(MainSupport.class);
    protected static final int UNINITIALIZED_EXIT_CODE = Integer.MIN_VALUE;
    protected static final int DEFAULT_EXIT_CODE = 0;
    protected final List<MainListener> listeners = new ArrayList<MainListener>();
    protected final List<Option> options = new ArrayList<Option>();
    protected final CountDownLatch latch = new CountDownLatch(1);
    protected final AtomicBoolean completed = new AtomicBoolean(false);
    protected final AtomicInteger exitCode = new AtomicInteger(UNINITIALIZED_EXIT_CODE);
    protected long duration = -1;
    protected long durationIdle = -1;
    protected int durationMaxMessages;
    protected TimeUnit timeUnit = TimeUnit.SECONDS;
    protected boolean trace;
    protected List<RouteBuilder> routeBuilders = new ArrayList<RouteBuilder>();
    protected String routeBuilderClasses;
    protected String fileWatchDirectory;
    protected boolean fileWatchDirectoryRecursively;
    protected final List<CamelContext> camelContexts = new ArrayList<CamelContext>();
    protected ProducerTemplate camelTemplate;
    protected boolean hangupInterceptorEnabled = true;
    protected int durationHitExitCode = DEFAULT_EXIT_CODE;
    protected ReloadStrategy reloadStrategy;

    /**
     * A class for intercepting the hang up signal and do a graceful shutdown of the Camel.
     */
    private static final class HangupInterceptor extends Thread {
        Logger log = LoggerFactory.getLogger(this.getClass());
        final MainSupport mainInstance;

        HangupInterceptor(MainSupport main) {
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
                "Sets the time duration (seconds) that the application will run for before terminating.",
                "duration") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                // skip second marker to be backwards compatible
                if (parameter.endsWith("s") || parameter.endsWith("S")) {
                    parameter = parameter.substring(0, parameter.length() - 1);
                }
                setDuration(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("dm", "durationMaxMessages",
                "Sets the duration of maximum number of messages that the application will process before terminating.",
                "durationMaxMessages") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDurationMaxMessages(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("di", "durationIdle",
                "Sets the idle time duration (seconds) duration that the application can be idle before terminating.",
                "durationIdle") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                // skip second marker to be backwards compatible
                if (parameter.endsWith("s") || parameter.endsWith("S")) {
                    parameter = parameter.substring(0, parameter.length() - 1);
                }
                setDurationIdle(Integer.parseInt(parameter));
            }
        });
        addOption(new Option("t", "trace", "Enables tracing") {
            protected void doProcess(String arg, LinkedList<String> remainingArgs) {
                enableTrace();
            }
        });
        addOption(new ParameterOption("e", "exitcode",
                "Sets the exit code if duration was hit",
                "exitcode")  {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setDurationHitExitCode(Integer.parseInt(parameter));
            }
        });
        addOption(new ParameterOption("watch", "fileWatch",
                "Sets a directory to watch for file changes to trigger reloading routes on-the-fly",
                "fileWatch") {
            @Override
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setFileWatchDirectory(parameter);
            }
        });
    }

    /**
     * Runs this process with the given arguments, and will wait until completed, or the JVM terminates.
     */
    public void run() throws Exception {
        if (!completed.get()) {
            internalBeforeStart();
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
     * Disable the hangup support. No graceful stop by calling stop() on a
     * Hangup signal.
     */
    public void disableHangupSupport() {
        hangupInterceptorEnabled = false;
    }

    /**
     * Hangup support is enabled by default.
     *
     * @deprecated is enabled by default now, so no longer need to call this method.
     */
    @Deprecated
    public void enableHangupSupport() {
        hangupInterceptorEnabled = true;
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

    private void internalBeforeStart() {
        if (hangupInterceptorEnabled) {
            String threadName = ThreadHelper.resolveThreadName(null, "CamelHangupInterceptor");

            Thread task = new HangupInterceptor(this);
            task.setName(threadName);
            Runtime.getRuntime().addShutdownHook(task);
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
        exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, DEFAULT_EXIT_CODE);
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
     * Sets the duration (in seconds) to run the application until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getDurationIdle() {
        return durationIdle;
    }

    /**
     * Sets the maximum idle duration (in seconds) when running the application, and
     * if there has been no message processed after being idle for more than this duration
     * then the application should be terminated.
     * Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDurationIdle(long durationIdle) {
        this.durationIdle = durationIdle;
    }

    public int getDurationMaxMessages() {
        return durationMaxMessages;
    }

    /**
     * Sets the duration to run the application to process at most max messages until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     */
    public void setDurationMaxMessages(int durationMaxMessages) {
        this.durationMaxMessages = durationMaxMessages;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * Sets the time unit duration (seconds by default).
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    /**
     * Sets the exit code for the application if duration was hit
     */
    public void setDurationHitExitCode(int durationHitExitCode) {
        this.durationHitExitCode = durationHitExitCode;
    }

    public int getDurationHitExitCode() {
        return durationHitExitCode;
    }

    public int getExitCode() {
        return exitCode.get();
    }

    public void setRouteBuilderClasses(String builders) {
        this.routeBuilderClasses = builders;
    }

    public String getFileWatchDirectory() {
        return fileWatchDirectory;
    }

    /**
     * Sets the directory name to watch XML file changes to trigger live reload of Camel routes.
     * <p/>
     * Notice you cannot set this value and a custom {@link ReloadStrategy} as well.
     */
    public void setFileWatchDirectory(String fileWatchDirectory) {
        this.fileWatchDirectory = fileWatchDirectory;
    }
    
    public boolean isFileWatchDirectoryRecursively() {
        return fileWatchDirectoryRecursively;
    }
    
    /**
     * Sets the flag to watch directory of XML file changes recursively to trigger live reload of Camel routes.
     * <p/>
     * Notice you cannot set this value and a custom {@link ReloadStrategy} as well.
     */
    public void setFileWatchDirectoryRecursively(boolean fileWatchDirectoryRecursively) {
        this.fileWatchDirectoryRecursively = fileWatchDirectoryRecursively;
    }

    public String getRouteBuilderClasses() {
        return routeBuilderClasses;
    }

    public ReloadStrategy getReloadStrategy() {
        return reloadStrategy;
    }

    /**
     * Sets a custom {@link ReloadStrategy} to be used.
     * <p/>
     * Notice you cannot set this value and the fileWatchDirectory as well.
     */
    public void setReloadStrategy(ReloadStrategy reloadStrategy) {
        this.reloadStrategy = reloadStrategy;
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
                    LOG.info("Waiting for: {} {}", duration, unit);
                    latch.await(duration, unit);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, durationHitExitCode);
                    completed.set(true);
                } else if (durationIdle > 0) {
                    TimeUnit unit = getTimeUnit();
                    LOG.info("Waiting to be idle for: {} {}", duration, unit);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, durationHitExitCode);
                    latch.await();
                    completed.set(true);
                } else if (durationMaxMessages > 0) {
                    LOG.info("Waiting until: {} messages has been processed", durationMaxMessages);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, durationHitExitCode);
                    latch.await();
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
        LOG.info("MainSupport exiting code: {}", getExitCode());
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
            answer.addAll(camelContext.getRouteDefinitions());
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
        if (fileWatchDirectory != null) {
            ReloadStrategy reload = new FileWatcherReloadStrategy(fileWatchDirectory, fileWatchDirectoryRecursively);
            camelContext.setReloadStrategy(reload);
            // ensure reload is added as service and started
            camelContext.addService(reload);
            // and ensure its register in JMX (which requires manually to be added because CamelContext is already started)
            Object managedObject = camelContext.getManagementStrategy().getManagementObjectStrategy().getManagedObjectForService(camelContext, reload);
            if (managedObject == null) {
                // service should not be managed
                return;
            }

            // skip already managed services, for example if a route has been restarted
            if (camelContext.getManagementStrategy().isManaged(managedObject, null)) {
                LOG.trace("The service is already managed: {}", reload);
                return;
            }

            try {
                camelContext.getManagementStrategy().manageObject(managedObject);
            } catch (Exception e) {
                LOG.warn("Could not register service: " + reload + " as Service MBean.", e);
            }
        }

        if (durationMaxMessages > 0 || durationIdle > 0) {
            // convert to seconds as that is what event notifier uses
            long seconds = timeUnit.toSeconds(durationIdle);
            // register lifecycle so we can trigger to shutdown the JVM when maximum number of messages has been processed
            EventNotifier notifier = new MainDurationEventNotifier(camelContext, durationMaxMessages, seconds, completed, latch, true);
            // register our event notifier
            ServiceHelper.startService(notifier);
            camelContext.getManagementStrategy().addEventNotifier(notifier);
        }

        // try to load the route builders from the routeBuilderClasses
        loadRouteBuilders(camelContext);
        for (RouteBuilder routeBuilder : routeBuilders) {
            camelContext.addRoutes(routeBuilder);
        }
        // register lifecycle so we are notified in Camel is stopped from JMX or somewhere else
        camelContext.addLifecycleStrategy(new MainLifecycleStrategy(completed, latch));
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
