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

import java.util.LinkedList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.EventNotifier;
import org.apache.camel.support.service.ServiceHelper;
import org.apache.camel.util.concurrent.ThreadHelper;
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
    protected final CountDownLatch latch = new CountDownLatch(1);

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

    protected MainSupport(Class... configurationClasses) {
        this();
        addConfigurationClass(configurationClasses);
    }

    protected MainSupport() {
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
                LOG.error("Failed: {}", e, e);
            }
        }
    }

    /**
     * Disable the hangup support. No graceful stop by calling stop() on a
     * Hangup signal.
     */
    public void disableHangupSupport() {
        mainConfigurationProperties.setHangupInterceptorEnabled(false);
    }

    /**
     * Hangup support is enabled by default.
     */
    public void enableHangupSupport() {
        mainConfigurationProperties.setHangupInterceptorEnabled(true);
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
        if (mainConfigurationProperties.isHangupInterceptorEnabled()) {
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
     * Gets the complete task which allows to trigger this on demand.
     */
    public Runnable getCompleteTask() {
        return this::completed;
    }

    @Deprecated
    public int getDuration() {
        return mainConfigurationProperties.getDurationMaxSeconds();
    }

    /**
     * Sets the duration (in seconds) to run the application until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
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
     * Sets the maximum idle duration (in seconds) when running the application, and
     * if there has been no message processed after being idle for more than this duration
     * then the application should be terminated.
     * Defaults to -1. Any value <= 0 will run forever.
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
     * Sets the duration to run the application to process at most max messages until it
     * should be terminated. Defaults to -1. Any value <= 0 will run forever.
     * @deprecated use {@link #configure()}
     */
    @Deprecated
    public void setDurationMaxMessages(int durationMaxMessages) {
        mainConfigurationProperties.setDurationMaxMessages(durationMaxMessages);
    }

    /**
     * Sets the exit code for the application if duration was hit
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
        if (mainConfigurationProperties.getDurationMaxMessages() > 0 || mainConfigurationProperties.getDurationMaxIdleSeconds() > 0) {
            // register lifecycle so we can trigger to shutdown the JVM when maximum number of messages has been processed
            EventNotifier notifier = new MainDurationEventNotifier(
                camelContext,
                mainConfigurationProperties.getDurationMaxMessages(),
                mainConfigurationProperties.getDurationMaxIdleSeconds(),
                completed,
                latch,
                true);

            // register our event notifier
            ServiceHelper.startService(notifier);
            camelContext.getManagementStrategy().addEventNotifier(notifier);
        }

        // register lifecycle so we are notified in Camel is stopped from JMX or somewhere else
        camelContext.addLifecycleStrategy(new MainLifecycleStrategy(completed, latch));
    }

    protected void waitUntilCompleted() {
        while (!completed.get()) {
            try {
                int idle = mainConfigurationProperties.getDurationMaxIdleSeconds();
                int max = mainConfigurationProperties.getDurationMaxMessages();
                long sec = mainConfigurationProperties.getDurationMaxSeconds();
                if (sec > 0) {
                    LOG.info("Waiting for: {} seconds", sec);
                    latch.await(sec, TimeUnit.SECONDS);
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, mainConfigurationProperties.getDurationHitExitCode());
                    completed.set(true);
                } else if (idle > 0 || max > 0) {
                    if (idle > 0 && max > 0) {
                        LOG.info("Waiting to be idle for: {} seconds or until: {} messages has been processed", idle, max);
                    } else if (idle > 0) {
                        LOG.info("Waiting to be idle for: {} seconds", idle);
                    } else {
                        LOG.info("Waiting until: {} messages has been processed", max);
                    }
                    exitCode.compareAndSet(UNINITIALIZED_EXIT_CODE, mainConfigurationProperties.getDurationHitExitCode());
                    latch.await();
                    completed.set(true);
                } else {
                    latch.await();
                }
            } catch (InterruptedException e) {
                // okay something interrupted us so terminate
                completed.set(true);
                latch.countDown();
                Thread.currentThread().interrupt();
            }
        }
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

}
