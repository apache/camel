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
package org.apache.camel.component.quartz;

import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.Component;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.CamelEvent;
import org.apache.camel.support.EventNotifierSupport;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.camel.test.junit6.TestExecutionConfiguration;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.listeners.JobListenerSupport;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A Quartz scheduler from the registry (such as the one of spring-boot-starter-quartz) is autowired into the quartz
 * component, and is often started before the CamelContext.
 */
class QuartzInjectedSchedulerTest extends CamelTestSupport {

    // a part of the WARN for a scheduler that is running when the component starts
    private static final String RUNNING = "spring.quartz.auto-startup=false";
    // a part of the WARN for options that are not used with such a scheduler
    private static final String IGNORED = "are ignored";

    private final List<JobExecutionException> jobFailures = new CopyOnWriteArrayList<>();
    private final List<String> jobsExecuted = new CopyOnWriteArrayList<>();
    private final List<String> warnings = new CopyOnWriteArrayList<>();
    private final AtomicInteger eventsSeen = new AtomicInteger();
    private final AbstractAppender appender
            = new AbstractAppender("QuartzInjectedScheduler-" + UUID.randomUUID(), null, null, true, Property.EMPTY_ARRAY) {
                @Override
                public void append(LogEvent event) {
                    eventsSeen.incrementAndGet();
                    if (event.getLevel() == Level.WARN) {
                        warnings.add(event.getMessage().getFormattedMessage());
                    }
                }
            };

    private Scheduler injected;
    private volatile boolean camelStartedEventReached;

    @Override
    public void configureTest(TestExecutionConfiguration testExecutionConfiguration) {
        // each test configures the registry and the routes before it starts the context
        testExecutionConfiguration.withUseRouteBuilder(false);
    }

    @BeforeEach
    void captureWarnings() {
        // the test classes run in parallel: while another one is still starting log4j, the logger has the default
        // configuration, which log4j replaces once it has read log4j2.properties, and the appender attached to it is
        // stopped and lost (CAMEL-27281), so attach it once log4j has started
        LoggerContext logging = componentLogger().getContext();
        await().atMost(30, TimeUnit.SECONDS).until(logging::isStarted);
        appender.start();
        componentLogger().addAppender(appender);
        // addAppender creates a non-additive logger config, which would hide this logger from the root appenders
        componentLogger().setAdditive(true);
    }

    @AfterEach
    void stopCapture() throws SchedulerException {
        componentLogger().removeAppender(appender);
        appender.stop();
        if (injected != null) {
            // Camel does not shut down a scheduler it did not create: stop Camel first, as an application does
            context.stop();
            injected.shutdown();
        }
    }

    @Test
    void runningInjectedSchedulerRunsJobThatFiresBeforeCamelContextIsFullyStarted() throws Exception {
        injected = createScheduler(2);
        // as the SchedulerFactoryBean of Spring does before Camel starts
        injected.start();
        context.getRegistry().bind("myScheduler", injected);
        // holds the start of Camel at the CamelContextStartedEvent, after the routes have started and before the
        // CamelContext is fully started, until the trigger has fired (the job waits for this point, see
        // createScheduler)
        context.getManagementStrategy().addEventNotifier(new EventNotifierSupport() {
            @Override
            public void notify(CamelEvent event) {
                camelStartedEventReached = true;
                await().atMost(10, TimeUnit.SECONDS).until(() -> !jobsExecuted.isEmpty());
            }

            @Override
            public boolean isEnabled(CamelEvent event) {
                return event instanceof CamelEvent.CamelContextStartedEvent;
            }
        });
        MockEndpoint mock = addRoute();

        context.start();

        assertSame(injected, context.getComponent("quartz", QuartzComponent.class).getScheduler());
        assertEquals(List.of(), jobFailures);
        mock.assertIsSatisfied();
        assertEquals(1, warningsContaining(injected, RUNNING), this::captureDiagnostics);
    }

    @Test
    void injectedSchedulerThatIsNotStartedIsStartedByCamel() throws Exception {
        injected = createScheduler(2);
        context.getRegistry().bind("myScheduler", injected);
        camelStartedEventReached = true;
        MockEndpoint mock = addRoute();

        context.start();

        mock.assertIsSatisfied();
        assertSame(injected, context.getComponent("quartz", QuartzComponent.class).getScheduler());
        assertTrue(injected.isStarted());
        assertEquals(List.of(), jobFailures);
        assertEquals(0, warningsContaining(injected, RUNNING), this::captureDiagnostics);
    }

    @Test
    void schedulerOfComponentRunsJob() throws Exception {
        MockEndpoint mock = addRoute();

        context.start();

        mock.assertIsSatisfied();
        Scheduler scheduler = context.getComponent("quartz", QuartzComponent.class).getScheduler();
        assertEquals(0, warningsContaining(scheduler, RUNNING), this::captureDiagnostics);
    }

    @Test
    void warnsWhenPropertiesAreSetWithInjectedScheduler() throws Exception {
        injected = createScheduler(2);
        context.getRegistry().bind("myScheduler", injected);
        QuartzComponent quartz = context.getComponent("quartz", QuartzComponent.class);
        quartz.setProperties(Map.of("org.quartz.threadPool.threadCount", "3"));
        quartz.setPropertiesFile("classpath:org/quartz/quartz.properties");

        context.start();

        assertSame(injected, quartz.getScheduler());
        assertEquals(2, quartz.getScheduler().getMetaData().getThreadPoolSize());
        assertEquals(1, warningsContaining(injected, IGNORED), this::captureDiagnostics);
        assertEquals(1, warningsContaining(injected, "properties, propertiesFile"), this::captureDiagnostics);
    }

    @Test
    void usesPropertiesWithoutInjectedScheduler() throws Exception {
        QuartzComponent quartz = context.getComponent("quartz", QuartzComponent.class);
        quartz.setProperties(Map.of(
                "org.quartz.scheduler.instanceName", "MyScheduler",
                "org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool",
                "org.quartz.threadPool.threadCount", "3"));

        context.start();

        assertEquals(3, quartz.getScheduler().getMetaData().getThreadPoolSize());
        assertEquals(0, warningsContaining(quartz.getScheduler(), IGNORED), this::captureDiagnostics);
    }

    private Scheduler createScheduler(int threadCount) throws SchedulerException {
        Properties props = new Properties();
        props.setProperty("org.quartz.scheduler.instanceName", "MyInjectedScheduler-" + UUID.randomUUID());
        props.setProperty("org.quartz.scheduler.skipUpdateCheck", "true");
        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", String.valueOf(threadCount));
        props.setProperty("org.quartz.jobStore.class", "org.quartz.simpl.RAMJobStore");
        Scheduler scheduler = new StdSchedulerFactory(props).getScheduler();
        scheduler.getListenerManager().addJobListener(new JobListenerSupport() {
            @Override
            public String getName() {
                return "jobsOfTest";
            }

            @Override
            public void jobToBeExecuted(JobExecutionContext ctx) {
                // the job runs once the routes have started, while Camel waits at the CamelContextStartedEvent
                await().atMost(10, TimeUnit.SECONDS).until(() -> camelStartedEventReached);
            }

            @Override
            public void jobWasExecuted(JobExecutionContext ctx, JobExecutionException jobException) {
                if (jobException != null) {
                    jobFailures.add(jobException);
                }
                jobsExecuted.add(ctx.getJobDetail().getKey().toString());
            }
        });
        return scheduler;
    }

    private MockEndpoint addRoute() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // fires once, at once
                from("quartz://myGroup/myTimer?trigger.repeatCount=0&triggerStartDelay=-1000").routeId("myRoute")
                        .to("mock:result");
            }
        });
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        return mock;
    }

    /**
     * Counts the WARNs about this scheduler only: the appender also gets the WARNs of the other test classes, which run
     * at the same time (QuartzComponentCamelContextSharedSchedulerTest logs the WARN of a running scheduler).
     */
    private long warningsContaining(Scheduler scheduler, String text) throws SchedulerException {
        String name = " " + scheduler.getSchedulerName() + " ";
        return warnings.stream().filter(message -> message.contains(name) && message.contains(text)).count();
    }

    /**
     * The state of the WARN capture, for the message of a failed assertion: tells whether the appender was attached and
     * received events, or the WARN was not logged (CAMEL-27281).
     */
    private String captureDiagnostics() {
        Logger logger = componentLogger();
        return "warnings=" + warnings
               + ", eventsSeenByAppender=" + eventsSeen.get()
               + ", appenderStarted=" + appender.isStarted()
               + ", appenderAttached=" + logger.getAppenders().containsKey(appender.getName())
               + ", loggerConfig=" + logger.get().getName()
               + ", additive=" + logger.isAdditive()
               + ", warnEnabled=" + logger.isWarnEnabled()
               + ", loggingStarted=" + logger.getContext().isStarted()
               + ", loggingConfiguration=" + logger.getContext().getConfiguration().getClass().getSimpleName()
               + ", quartzComponentStatus=" + quartzComponentStatus()
               + ", thread=" + Thread.currentThread().getName();
    }

    private String quartzComponentStatus() {
        Component quartz = context.hasComponent("quartz");
        return quartz instanceof QuartzComponent qc ? String.valueOf(qc.getStatus()) : "none";
    }

    private static Logger componentLogger() {
        return (Logger) LogManager.getLogger(QuartzComponent.class);
    }
}
