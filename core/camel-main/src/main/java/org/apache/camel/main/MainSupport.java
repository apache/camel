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
package org.apache.camel.main;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelConfiguration;
import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.service.ServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class for main implementations to allow starting up a JVM with Camel embedded.
 */
public abstract class MainSupport extends BaseMainSupport {

    protected static final Logger LOG = LoggerFactory.getLogger(MainSupport.class);

    protected static final int UNINITIALIZED_EXIT_CODE = Integer.MIN_VALUE;
    protected static final int DEFAULT_EXIT_CODE = 0;

    protected final AtomicInteger exitCode = new AtomicInteger(UNINITIALIZED_EXIT_CODE);
    protected MainShutdownStrategy shutdownStrategy;

    protected volatile ProducerTemplate camelTemplate;

    private String appName = "Apache Camel (Main)";
    private int durationMaxIdleSeconds;
    private int durationMaxMessages;
    private long durationMaxSeconds;
    private int durationHitExitCode;
    private String durationMaxAction = "shutdown";

    @SafeVarargs
    protected MainSupport(Class<? extends CamelConfiguration>... configurationClasses) {
        this();
        for (Class<? extends CamelConfiguration> clazz : configurationClasses) {
            configure().addConfiguration(clazz);
        }
    }

    protected MainSupport() {
        this.shutdownStrategy = new DefaultMainShutdownStrategy(this);
    }

    @Override
    protected void doInit() throws Exception {
        // we want this logging to be as early as possible
        LOG.info("{} {} is starting", appName, helper.getVersion());
        super.doInit();
    }

    @Override
    protected void autoconfigure(CamelContext camelContext) throws Exception {
        super.autoconfigure(camelContext);

        // additional main configuration after auto-configuration
        shutdownStrategy.setExtraShutdownTimeout(configure().getExtraShutdownTimeout());
    }

    /**
     * Runs this process with the given arguments, and will wait until completed, or the JVM terminates.
     */
    public void run() throws Exception {
        if (shutdownStrategy.isRunAllowed()) {
            init();
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
                // while running then just log errors
                LOG.error("Failed: {}", e, e);
            }
            LOG.info("{} {} shutdown", appName, helper.getVersion());
        }
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
     * Tasks to run before start() is called.
     */
    protected void internalBeforeStart() {
        // used while waiting to be done
        durationMaxIdleSeconds = mainConfigurationProperties.getDurationMaxIdleSeconds();
        durationMaxMessages = mainConfigurationProperties.getDurationMaxMessages();
        durationMaxSeconds = mainConfigurationProperties.getDurationMaxSeconds();
        durationHitExitCode = mainConfigurationProperties.getDurationHitExitCode();
        durationMaxAction = mainConfigurationProperties.getDurationMaxAction();

        // register main as bootstrap
        registerMainBootstrap();
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
        shutdownStrategy.shutdown();
        exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, DEFAULT_EXIT_CODE);
    }

    /**
     * Gets the complete task which allows to trigger this on demand.
     */
    public Runnable getCompleteTask() {
        return this::completed;
    }

    /**
     * Registers {@link MainBootstrapCloseable} with the CamelContext.
     */
    protected void registerMainBootstrap() {
        CamelContext context = getCamelContext();
        if (context != null) {
            context.adapt(ExtendedCamelContext.class).addBootstrap(new MainBootstrapCloseable(this));
        }
    }

    @Deprecated
    public int getDuration() {
        return mainConfigurationProperties.getDurationMaxSeconds();
    }

    /**
     * Sets the duration (in seconds) to run the application until it should be terminated. Defaults to -1. Any value <=
     * 0 will run forever.
     *
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDuration(int duration) {
        mainConfigurationProperties.setDurationMaxSeconds(duration);
    }

    @Deprecated
    public int getDurationIdle() {
        return mainConfigurationProperties.getDurationMaxIdleSeconds();
    }

    /**
     * Sets the maximum idle duration (in seconds) when running the application, and if there has been no message
     * processed after being idle for more than this duration then the application should be terminated. Defaults to -1.
     * Any value <= 0 will run forever.
     *
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDurationIdle(int durationIdle) {
        mainConfigurationProperties.setDurationMaxIdleSeconds(durationIdle);
    }

    @Deprecated
    public int getDurationMaxMessages() {
        return mainConfigurationProperties.getDurationMaxMessages();
    }

    /**
     * Sets the duration to run the application to process at most max messages until it should be terminated. Defaults
     * to -1. Any value <= 0 will run forever.
     *
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDurationMaxMessages(int durationMaxMessages) {
        mainConfigurationProperties.setDurationMaxMessages(durationMaxMessages);
    }

    /**
     * Sets the exit code for the application if duration was hit
     *
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDurationHitExitCode(int durationHitExitCode) {
        mainConfigurationProperties.setDurationHitExitCode(durationHitExitCode);
    }

    @Deprecated
    public int getDurationHitExitCode() {
        return mainConfigurationProperties.getDurationHitExitCode();
    }

    public int getExitCode() {
        return exitCode.get();
    }

    public boolean isTrace() {
        return mainConfigurationProperties.isTracing();
    }

    public void enableTrace() {
        mainConfigurationProperties.setTracing(true);
    }

    public void enableTraceStandby() {
        mainConfigurationProperties.setTracingStandby(true);
    }

    public MainShutdownStrategy getShutdownStrategy() {
        return shutdownStrategy;
    }

    /**
     * Set the {@link MainShutdownStrategy} used to properly shut-down the main instance. By default a
     * {@link DefaultMainShutdownStrategy} will be used.
     *
     * @param shutdownStrategy the shutdown strategy
     */
    public void setShutdownStrategy(MainShutdownStrategy shutdownStrategy) {
        this.shutdownStrategy = shutdownStrategy;
    }

    public String getAppName() {
        return appName;
    }

    /**
     * Application name (used for logging start and stop)
     */
    public void setAppName(String appName) {
        this.appName = appName;
    }

    @Override
    protected void doStop() throws Exception {
        // call completed to properly stop as we count down the waiting latch
        completed();
    }

    @Override
    protected void doStart() throws Exception {
    }

    @Override
    protected void configureLifecycle(CamelContext camelContext) throws Exception {
        if (mainConfigurationProperties.getDurationMaxSeconds() > 0
                || mainConfigurationProperties.getDurationMaxMessages() > 0
                || mainConfigurationProperties.getDurationMaxIdleSeconds() > 0) {
            // register lifecycle, so we can trigger to shutdown the JVM when maximum number of messages has been processed
            // (we must use the event notifier also for max seconds only to support restarting duration if routes are reloaded)
            EventNotifier notifier = new MainDurationEventNotifier(
                    camelContext,
                    mainConfigurationProperties.getDurationMaxMessages(),
                    mainConfigurationProperties.getDurationMaxIdleSeconds(),
                    shutdownStrategy,
                    true,
                    mainConfigurationProperties.isRoutesReloadRestartDuration(),
                    mainConfigurationProperties.getDurationMaxAction());

            // register our event notifier
            ServiceHelper.startService(notifier);
            camelContext.getManagementStrategy().addEventNotifier(notifier);
        }

        // register lifecycle, so we are notified in Camel is stopped from JMX or somewhere else
        camelContext.addLifecycleStrategy(new MainLifecycleStrategy(shutdownStrategy));
    }

    protected void waitUntilCompleted() {
        while (shutdownStrategy.isRunAllowed()) {
            try {
                int idle = durationMaxIdleSeconds;
                int max = durationMaxMessages;
                long sec = durationMaxSeconds;
                int exit = durationHitExitCode;
                if (sec > 0) {
                    LOG.info("Waiting until complete: Duration max {} seconds", sec);
                    boolean zero = shutdownStrategy.await(sec, TimeUnit.SECONDS);
                    if (!zero) {
                        if ("stop".equalsIgnoreCase(durationMaxAction)) {
                            LOG.info("Duration max seconds triggering stopping all routes");
                            try {
                                camelContext.getRouteController().stopAllRoutes();
                            } catch (Exception e) {
                                LOG.warn("Error during stopping all routes. This exception is ignored.", e);
                            }
                            // we are just stopping routes (not terminating JVM) so continue
                            continue;
                        }
                    }
                    LOG.info("Duration max seconds triggering shutdown of the JVM");
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, exit);
                    shutdownStrategy.shutdown();
                } else if (idle > 0 || max > 0) {
                    if (idle > 0 && max > 0) {
                        LOG.info("Waiting until complete: Duration idle {} seconds or max {} messages processed", idle, max);
                    } else if (idle > 0) {
                        LOG.info("Waiting until complete: Duration idle {} seconds", idle);
                    } else {
                        LOG.info("Waiting until complete: Duration max {} messages processed", max);
                    }
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, exit);
                    shutdownStrategy.await();
                    shutdownStrategy.shutdown();
                } else {
                    shutdownStrategy.await();
                }
            } catch (InterruptedException e) {
                // okay something interrupted us so terminate
                shutdownStrategy.shutdown();
                Thread.currentThread().interrupt();
            }
        }
    }

    protected abstract ProducerTemplate findOrCreateCamelTemplate();

    protected abstract CamelContext createCamelContext();

    public ProducerTemplate getCamelTemplate() throws Exception {
        if (camelTemplate == null) {
            camelTemplate = findOrCreateCamelTemplate();
        }
        return camelTemplate;
    }

    protected void initCamelContext() throws Exception {
        camelContext = createCamelContext();
        if (camelContext == null) {
            throw new IllegalStateException("Created CamelContext is null");
        }
        postProcessCamelContext(camelContext);
    }
}
