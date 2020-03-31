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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExtendedStartupListener;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.PropertiesHelper;
import org.apache.camel.util.StringHelper;
import org.quartz.Scheduler;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerKey;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This component will hold a Quartz Scheduler that will provide scheduled timer based
 * endpoint that generate a QuartzMessage to a route. Currently it support Cron and Simple trigger scheduling type.
 */
@Component("quartz")
public class QuartzComponent extends DefaultComponent implements ExtendedStartupListener {

    private static final Logger LOG = LoggerFactory.getLogger(QuartzComponent.class);

    private final List<SchedulerInitTask> schedulerInitTasks = new ArrayList<>();
    private volatile boolean schedulerInitTasksDone;

    @Metadata(label = "advanced")
    private Scheduler scheduler;
    @Metadata(label = "advanced")
    private SchedulerFactory schedulerFactory;
    @Metadata
    private String propertiesRef;
    @Metadata
    private Map properties;
    @Metadata
    private String propertiesFile;
    @Metadata(label = "scheduler")
    private int startDelayedSeconds;
    @Metadata(label = "scheduler", defaultValue = "true")
    private boolean autoStartScheduler = true;
    @Metadata(label = "scheduler")
    private boolean interruptJobsOnShutdown;
    @Metadata(defaultValue = "true")
    private boolean enableJmx = true;
    @Metadata
    private boolean prefixJobNameWithEndpointId;
    @Metadata(defaultValue = "true")
    private boolean prefixInstanceName = true;

    public QuartzComponent() {
    }

    public QuartzComponent(CamelContext camelContext) {
        super(camelContext);
    }

    public boolean isAutoStartScheduler() {
        return autoStartScheduler;
    }

    /**
     * Whether or not the scheduler should be auto started.
     * <p/>
     * This options is default true
     */
    public void setAutoStartScheduler(boolean autoStartScheduler) {
        this.autoStartScheduler = autoStartScheduler;
    }

    public int getStartDelayedSeconds() {
        return startDelayedSeconds;
    }

    /**
     * Seconds to wait before starting the quartz scheduler.
     */
    public void setStartDelayedSeconds(int startDelayedSeconds) {
        this.startDelayedSeconds = startDelayedSeconds;
    }

    public boolean isPrefixJobNameWithEndpointId() {
        return prefixJobNameWithEndpointId;
    }

    /**
     * Whether to prefix the quartz job with the endpoint id.
     * <p/>
     * This option is default false.
     */
    public void setPrefixJobNameWithEndpointId(boolean prefixJobNameWithEndpointId) {
        this.prefixJobNameWithEndpointId = prefixJobNameWithEndpointId;
    }

    public boolean isEnableJmx() {
        return enableJmx;
    }

    /**
     * Whether to enable Quartz JMX which allows to manage the Quartz scheduler from JMX.
     * <p/>
     * This options is default true
     */
    public void setEnableJmx(boolean enableJmx) {
        this.enableJmx = enableJmx;
    }

    public String getPropertiesRef() {
        return propertiesRef;
    }

    /**
     * References to an existing {@link Properties} or {@link Map} to lookup in the registry to use for configuring quartz.
     */
    public void setPropertiesRef(String propertiesRef) {
        this.propertiesRef = propertiesRef;
    }

    public Map getProperties() {
        return properties;
    }

    /**
     * Properties to configure the Quartz scheduler.
     */
    public void setProperties(Map properties) {
        this.properties = properties;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    /**
     * File name of the properties to load from the classpath
     */
    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public boolean isPrefixInstanceName() {
        return prefixInstanceName;
    }

    /**
     * Whether to prefix the Quartz Scheduler instance name with the CamelContext name.
     * <p/>
     * This is enabled by default, to let each CamelContext use its own Quartz scheduler instance by default.
     * You can set this option to <tt>false</tt> to reuse Quartz scheduler instances between multiple CamelContext's.
     */
    public void setPrefixInstanceName(boolean prefixInstanceName) {
        this.prefixInstanceName = prefixInstanceName;
    }

    public boolean isInterruptJobsOnShutdown() {
        return interruptJobsOnShutdown;
    }

    /**
     * Whether to interrupt jobs on shutdown which forces the scheduler to shutdown quicker and attempt to interrupt any running jobs.
     * If this is enabled then any running jobs can fail due to being interrupted.
     */
    public void setInterruptJobsOnShutdown(boolean interruptJobsOnShutdown) {
        this.interruptJobsOnShutdown = interruptJobsOnShutdown;
    }

    public SchedulerFactory getSchedulerFactory() {
        if (schedulerFactory == null) {
            try {
                schedulerFactory = createSchedulerFactory();
            } catch (SchedulerException e) {
                throw new RuntimeException(e);
            }
        }
        return schedulerFactory;
    }

    private SchedulerFactory createSchedulerFactory() throws SchedulerException {
        SchedulerFactory answer;

        Properties prop = loadProperties();
        if (prop != null) {

            // force disabling update checker (will do online check over the internet)
            prop.put("org.quartz.scheduler.skipUpdateCheck", "true");
            prop.put("org.terracotta.quartz.skipUpdateCheck", "true");

            // camel context name will be a suffix to use one scheduler per context
            if (isPrefixInstanceName()) {
                String instName = createInstanceName(prop);
                prop.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, instName);
            }

            if (isInterruptJobsOnShutdown()) {
                prop.setProperty(StdSchedulerFactory.PROP_SCHED_INTERRUPT_JOBS_ON_SHUTDOWN, "true");
            }

            // enable jmx unless configured to not do so
            if (enableJmx && !prop.containsKey("org.quartz.scheduler.jmx.export")) {
                prop.put("org.quartz.scheduler.jmx.export", "true");
                LOG.info("Setting org.quartz.scheduler.jmx.export=true to ensure QuartzScheduler(s) will be enlisted in JMX.");
            }

            answer = new StdSchedulerFactory(prop);
        } else {
            // read default props to be able to use a single scheduler per camel context
            // if we need more than one scheduler per context use setScheduler(Scheduler)
            // or setFactory(SchedulerFactory) methods

            // must use classloader from StdSchedulerFactory to work even in OSGi
            InputStream is = StdSchedulerFactory.class.getClassLoader().getResourceAsStream("org/quartz/quartz.properties");
            if (is == null) {
                throw new SchedulerException("Quartz properties file not found in classpath: org/quartz/quartz.properties");
            }
            prop = new Properties();
            try {
                prop.load(is);
            } catch (IOException e) {
                throw new SchedulerException("Error loading Quartz properties file from classpath: org/quartz/quartz.properties", e);
            } finally {
                IOHelper.close(is);
            }

            // camel context name will be a suffix to use one scheduler per context
            if (isPrefixInstanceName()) {
                // camel context name will be a suffix to use one scheduler per context
                String instName = createInstanceName(prop);
                prop.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, instName);
            }

            // force disabling update checker (will do online check over the internet)
            prop.put("org.quartz.scheduler.skipUpdateCheck", "true");
            prop.put("org.terracotta.quartz.skipUpdateCheck", "true");

            if (isInterruptJobsOnShutdown()) {
                prop.setProperty(StdSchedulerFactory.PROP_SCHED_INTERRUPT_JOBS_ON_SHUTDOWN, "true");
            }

            // enable jmx unless configured to not do so
            if (enableJmx && !prop.containsKey("org.quartz.scheduler.jmx.export")) {
                prop.put("org.quartz.scheduler.jmx.export", "true");
                LOG.info("Setting org.quartz.scheduler.jmx.export=true to ensure QuartzScheduler(s) will be enlisted in JMX.");
            }

            answer = new StdSchedulerFactory(prop);
        }

        if (LOG.isDebugEnabled()) {
            String name = prop.getProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME);
            LOG.debug("Creating SchedulerFactory: {} with properties: {}", name, prop);
        }
        return answer;
    }

    protected String createInstanceName(Properties prop) {
        String instName = prop.getProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME);

        // camel context name will be a suffix to use one scheduler per context
        String identity = QuartzHelper.getQuartzContextName(getCamelContext());
        if (identity != null) {
            if (instName == null) {
                instName = "scheduler-" + identity;
            } else {
                instName = instName + "-" + identity;
            }
        }
        return instName;
    }

    /**
     * Is the quartz scheduler clustered?
     */
    public boolean isClustered() throws SchedulerException {
        return getScheduler().getMetaData().isJobStoreClustered();
    }

    private Properties loadProperties() throws SchedulerException {
        Properties answer = null;
        if (getProperties() != null) {
            answer = new Properties();
            answer.putAll(getProperties());
        }
        if (answer == null && getPropertiesRef() != null) {
            Map map = CamelContextHelper.mandatoryLookup(getCamelContext(), getPropertiesRef(), Map.class);
            answer = new Properties();
            answer.putAll(map);
        }
        if (answer == null && getPropertiesFile() != null) {
            LOG.info("Loading Quartz properties file from: {}", getPropertiesFile());
            InputStream is = null;
            try {
                is = ResourceHelper.resolveMandatoryResourceAsInputStream(getCamelContext(), getPropertiesFile());
                answer = new Properties();
                answer.load(is);
            } catch (IOException e) {
                throw new SchedulerException("Error loading Quartz properties file: " + getPropertiesFile(), e);
            } finally {
                IOHelper.close(is);
            }
        }
        return answer;
    }

    /**
     * To use the custom SchedulerFactory which is used to create the Scheduler.
     */
    public void setSchedulerFactory(SchedulerFactory schedulerFactory) {
        this.schedulerFactory = schedulerFactory;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * Adds a task to be executed as part of initializing and starting the scheduler; or
     * executes the task if the scheduler has already been started.
     */
    public void addScheduleInitTask(SchedulerInitTask task) {
        if (schedulerInitTasksDone) {
            // task already done then run task now
            try {
                task.initializeTask(scheduler);
            } catch (Exception e) {
                throw RuntimeCamelException.wrapRuntimeException(e);
            }
        } else {
            this.schedulerInitTasks.add(task);
        }
    }

    /**
     * To use the custom configured Quartz scheduler, instead of creating a new Scheduler.
     */
    public void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Get couple of scheduler settings
        Integer startDelayedSeconds = getAndRemoveParameter(parameters, "startDelayedSeconds", Integer.class);
        if (startDelayedSeconds != null) {
            if (this.startDelayedSeconds != 0 && !(this.startDelayedSeconds == startDelayedSeconds)) {
                LOG.warn("A Quartz job is already configured with a different 'startDelayedSeconds' configuration! "
                        + "All Quartz jobs must share the same 'startDelayedSeconds' configuration! Cannot apply the 'startDelayedSeconds' configuration!");
            } else {
                this.startDelayedSeconds = startDelayedSeconds;
            }
        }

        Boolean autoStartScheduler = getAndRemoveParameter(parameters, "autoStartScheduler", Boolean.class);
        if (autoStartScheduler != null) {
            this.autoStartScheduler = autoStartScheduler;
        }

        Boolean prefixJobNameWithEndpointId = getAndRemoveParameter(parameters, "prefixJobNameWithEndpointId", Boolean.class);
        if (prefixJobNameWithEndpointId != null) {
            this.prefixJobNameWithEndpointId = prefixJobNameWithEndpointId;
        }

        // Extract trigger.XXX and job.XXX properties to be set on endpoint below
        Map<String, Object> triggerParameters = PropertiesHelper.extractProperties(parameters, "trigger.");
        Map<String, Object> jobParameters = PropertiesHelper.extractProperties(parameters, "job.");

        // Create quartz endpoint
        QuartzEndpoint result = new QuartzEndpoint(uri, this);
        TriggerKey triggerKey = createTriggerKey(uri, remaining, result);
        result.setTriggerKey(triggerKey);
        result.setTriggerParameters(triggerParameters);
        result.setJobParameters(jobParameters);
        if (startDelayedSeconds != null) {
            result.setStartDelayedSeconds(startDelayedSeconds);
        }
        if (autoStartScheduler != null) {
            result.setAutoStartScheduler(autoStartScheduler);
        }
        if (prefixJobNameWithEndpointId != null) {
            result.setPrefixJobNameWithEndpointId(prefixJobNameWithEndpointId);
        }
        return result;
    }

    private TriggerKey createTriggerKey(String uri, String remaining, QuartzEndpoint endpoint) throws Exception {
        // Parse uri for trigger name and group
        URI u = new URI(uri);
        String path = StringHelper.after(u.getPath(), "/");
        String host = u.getHost();

        // host can be null if the uri did contain invalid host characters such as an underscore
        if (host == null) {
            host = StringHelper.before(remaining, "/");
            if (host == null) {
                host = remaining;
            }
        }

        // Trigger group can be optional, if so set it to this context's unique name
        String name;
        String group;
        if (ObjectHelper.isNotEmpty(path) && ObjectHelper.isNotEmpty(host)) {
            group = host;
            name = path;
        } else {
            String camelContextName = QuartzHelper.getQuartzContextName(getCamelContext());
            group = camelContextName == null ? "Camel" : "Camel_" + camelContextName;
            name = host;
        }

        if (prefixJobNameWithEndpointId) {
            name = endpoint.getId() + "_" + name;
        }

        return new TriggerKey(name, group);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        if (scheduler == null) {
            createAndInitScheduler();
        }
    }

    private void createAndInitScheduler() throws SchedulerException {
        LOG.info("Create and initializing scheduler.");
        scheduler = createScheduler();

        SchedulerContext quartzContext = storeCamelContextInQuartzContext();

        // Set camel job counts to zero. We needed this to prevent shutdown in case there are multiple Camel contexts
        // that has not completed yet, and the last one with job counts to zero will eventually shutdown.
        AtomicInteger number = (AtomicInteger) quartzContext.get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
        if (number == null) {
            number = new AtomicInteger(0);
            quartzContext.put(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT, number);
        }
    }

    private SchedulerContext storeCamelContextInQuartzContext() throws SchedulerException {
        // Store CamelContext into QuartzContext space
        SchedulerContext quartzContext = scheduler.getContext();
        String camelContextName = QuartzHelper.getQuartzContextName(getCamelContext());
        LOG.debug("Storing camelContextName={} into Quartz Context space.", camelContextName);
        quartzContext.put(QuartzConstants.QUARTZ_CAMEL_CONTEXT + "-" + camelContextName, getCamelContext());
        return quartzContext;
    }

    private Scheduler createScheduler() throws SchedulerException {
        return getSchedulerFactory().getScheduler();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (scheduler != null) {
            if (isInterruptJobsOnShutdown()) {
                LOG.info("Shutting down scheduler. (will interrupts jobs to shutdown quicker.)");
                scheduler.shutdown(false);
                scheduler = null;
            } else {
                AtomicInteger number = (AtomicInteger) scheduler.getContext().get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
                if (number != null && number.get() > 0) {
                    LOG.info("Cannot shutdown scheduler: " + scheduler.getSchedulerName() + " as there are still " + number.get() + " jobs registered.");
                } else {
                    LOG.info("Shutting down scheduler. (will wait for all jobs to complete first.)");
                    scheduler.shutdown(true);
                    scheduler = null;
                }
            }
        }
    }

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        if (alreadyStarted) {
            // a route may have been added or starter after CamelContext is started so ensure we startup the scheduler
            doStartScheduler();
        }
    }

    @Override
    public void onCamelContextFullyStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        doStartScheduler();
    }

    protected void doStartScheduler() throws Exception {
        // If Camel has already started and then user add a route dynamically, we need to ensure
        // to create and init the scheduler first.
        if (scheduler == null) {
            createAndInitScheduler();
        } else {
            // in case custom scheduler was injected (i.e. created elsewhere), we may need to add
            // current camel context to quartz context so jobs have access
            storeCamelContextInQuartzContext();
        }

        // initialize scheduler tasks
        for (SchedulerInitTask task : schedulerInitTasks) {
            task.initializeTask(scheduler);
        }
        // cleanup tasks as they need only to be triggered once
        schedulerInitTasks.clear();
        schedulerInitTasksDone = true;

        // Now scheduler is ready, let see how we should start it.
        if (!autoStartScheduler) {
            LOG.info("Not starting scheduler because autoStartScheduler is set to false.");
        } else {
            if (startDelayedSeconds > 0) {
                if (scheduler.isStarted()) {
                    LOG.warn("The scheduler has already started. Cannot apply the 'startDelayedSeconds' configuration!");
                } else {
                    LOG.info("Starting scheduler with startDelayedSeconds={}", startDelayedSeconds);
                    scheduler.startDelayed(startDelayedSeconds);
                }
            } else {
                if (scheduler.isStarted()) {
                    LOG.info("The scheduler has already been started.");
                } else {
                    LOG.info("Starting scheduler.");
                    scheduler.start();
                }
            }
        }
    }
}
