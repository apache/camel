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
package org.apache.camel.component.quartz;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.camel.CamelContext;
import org.apache.camel.StartupListener;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.EndpointHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ResourceHelper;
import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SchedulerFactory;
import org.quartz.SimpleTrigger;
import org.quartz.Trigger;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A <a href="http://camel.apache.org/quartz.html">Quartz Component</a>
 * <p/>
 * For a brief tutorial on setting cron expression see
 * <a href="http://quartz-scheduler.org/documentation/quartz-1.x/tutorials/crontrigger">Quartz cron tutorial</a>.
 *
 * @version
 */
public class QuartzComponent extends UriEndpointComponent implements StartupListener {
    private static final Logger LOG = LoggerFactory.getLogger(QuartzComponent.class);

    private final transient List<JobToAdd> jobsToAdd = new ArrayList<JobToAdd>();

    @Metadata(label = "advanced")
    private Scheduler scheduler;
    @Metadata(label = "advanced")
    private SchedulerFactory factory;
    private Properties properties;
    private String propertiesFile;
    @Metadata(label = "scheduler")
    private int startDelayedSeconds;
    @Metadata(defaultValue = "true")
    private boolean autoStartScheduler = true;
    @Metadata(defaultValue = "true")
    private boolean enableJmx = true;

    private static final class JobToAdd {
        private final JobDetail job;
        private final Trigger trigger;

        private JobToAdd(JobDetail job, Trigger trigger) {
            this.job = job;
            this.trigger = trigger;
        }

        public JobDetail getJob() {
            return job;
        }

        public Trigger getTrigger() {
            return trigger;
        }
    }

    public QuartzComponent() {
        super(QuartzEndpoint.class);
    }

    public QuartzComponent(final CamelContext context) {
        super(context, QuartzEndpoint.class);
    }

    @Override
    protected QuartzEndpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        // lets split the remaining into a group/name
        URI u = new URI(uri);
        String path = ObjectHelper.after(u.getPath(), "/");
        String host = u.getHost();
        String cron = getAndRemoveParameter(parameters, "cron", String.class);
        boolean fireNow = getAndRemoveParameter(parameters, "fireNow", Boolean.class, Boolean.FALSE);
        Integer startDelayedSeconds = getAndRemoveParameter(parameters, "startDelayedSeconds", Integer.class);
        if (startDelayedSeconds != null) {
            if (scheduler.isStarted()) {
                LOG.warn("A Quartz job is already started. Cannot apply the 'startDelayedSeconds' configuration!");
            } else if (this.startDelayedSeconds != 0 && !(this.startDelayedSeconds == startDelayedSeconds)) {
                LOG.warn("A Quartz job is already configured with a different 'startDelayedSeconds' configuration! "
                    + "All Quartz jobs must share the same 'startDelayedSeconds' configuration! Cannot apply the 'startDelayedSeconds' configuration!");
            } else {
                this.startDelayedSeconds = startDelayedSeconds;
            }
        }

        // host can be null if the uri did contain invalid host characters such as an underscore
        if (host == null) {
            host = ObjectHelper.before(remaining, "/");
            if (host == null) {
                host = remaining;
            }
        }

        // group can be optional, if so set it to Camel
        String name;
        String group;
        if (ObjectHelper.isNotEmpty(path) && ObjectHelper.isNotEmpty(host)) {
            group = host;
            name = path;
        } else {
            group = "Camel";
            name = host;
        }

        Map<String, Object> triggerParameters = IntrospectionSupport.extractProperties(parameters, "trigger.");
        Map<String, Object> jobParameters = IntrospectionSupport.extractProperties(parameters, "job.");

        Trigger trigger;
        boolean stateful = "true".equals(parameters.get("stateful"));

        // if we're starting up and not running in Quartz clustered mode or not stateful then check for a name conflict.
        if (!isClustered() && !stateful) {
            // check to see if this trigger already exists
            trigger = getScheduler().getTrigger(name, group);
            if (trigger != null) {
                String msg = "A Quartz job already exists with the name/group: " + name + "/" + group;
                throw new IllegalArgumentException(msg);
            }
        }

        // create the trigger either cron or simple
        if (ObjectHelper.isNotEmpty(cron)) {
            cron = encodeCronExpression(cron);
            trigger = createCronTrigger(cron);
        } else {
            trigger = new SimpleTrigger();
            if (fireNow) {
                String intervalString = (String) triggerParameters.get("repeatInterval");
                if (intervalString != null) {
                    long interval = EndpointHelper.resolveParameter(getCamelContext(), intervalString, Long.class);
                    
                    trigger.setStartTime(new Date(System.currentTimeMillis() - interval));
                }
            }
        }

        QuartzEndpoint answer = new QuartzEndpoint(uri, this);
        answer.setGroupName(group);
        answer.setTimerName(name);
        answer.setCron(cron);
        answer.setFireNow(fireNow);
        if (startDelayedSeconds != null) {
            answer.setStartDelayedSeconds(startDelayedSeconds);
        }
        if (triggerParameters != null && !triggerParameters.isEmpty()) {
            answer.setTriggerParameters(triggerParameters);
        }
        if (jobParameters != null && !jobParameters.isEmpty()) {
            answer.setJobParameters(jobParameters);
            setProperties(answer.getJobDetail(), jobParameters);
        }

        // enrich job data map with trigger information
        if (cron != null) {
            answer.getJobDetail().getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_TYPE, "cron");
            answer.getJobDetail().getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_CRON_EXPRESSION, cron);
            String timeZone = EndpointHelper.resolveParameter(getCamelContext(), (String)triggerParameters.get("timeZone"), String.class);
            if (timeZone != null) {
                answer.getJobDetail().getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_CRON_TIMEZONE, timeZone);
            }
        } else {
            answer.getJobDetail().getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_TYPE, "simple");
            Long interval = EndpointHelper.resolveParameter(getCamelContext(), (String)triggerParameters.get("repeatInterval"), Long.class);
            if (interval != null) {
                triggerParameters.put("repeatInterval", interval);
                answer.getJobDetail().getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_INTERVAL, interval);
            }
            Integer counter = EndpointHelper.resolveParameter(getCamelContext(), (String)triggerParameters.get("repeatCount"), Integer.class);
            if (counter != null) {
                triggerParameters.put("repeatCount", counter);
                answer.getJobDetail().getJobDataMap().put(QuartzConstants.QUARTZ_TRIGGER_SIMPLE_REPEAT_COUNTER, counter);
            }
        }

        setProperties(trigger, triggerParameters);
        trigger.setName(name);
        trigger.setGroup(group);
        answer.setTrigger(trigger);

        return answer;
    }

    protected CronTrigger createCronTrigger(String path) throws ParseException {
        CronTrigger cron = new CronTrigger();
        cron.setCronExpression(path);
        return cron;
    }

    private static String encodeCronExpression(String path) {
        // replace + back to space so it's a cron expression
        return path.replaceAll("\\+", " ");
    }

    public void onCamelContextStarted(CamelContext camelContext, boolean alreadyStarted) throws Exception {
        if (scheduler != null) {
            String uid = QuartzHelper.getQuartzContextName(camelContext);
            scheduler.getContext().put(QuartzConstants.QUARTZ_CAMEL_CONTEXT + "-" + uid, camelContext);
        }

        // if not configure to auto start then don't start it
        if (!isAutoStartScheduler()) {
            LOG.info("QuartzComponent configured to not auto start Quartz scheduler.");
            return;
        }

        // only start scheduler when CamelContext has finished starting
        startScheduler();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (scheduler == null) {
            scheduler = getScheduler();
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (scheduler != null) {
            AtomicInteger number = (AtomicInteger) scheduler.getContext().get("CamelJobs");
            if (number != null && number.get() > 0) {
                LOG.info("Cannot shutdown Quartz scheduler: " + scheduler.getSchedulerName() + " as there are still " + number.get() + " jobs registered.");
            } else {
                // no more jobs then shutdown the scheduler
                LOG.info("There are no more jobs registered, so shutting down Quartz scheduler: " + scheduler.getSchedulerName());
                scheduler.shutdown();
                scheduler = null;
            }
        }
    }

    public void addJob(JobDetail job, Trigger trigger) throws SchedulerException {
        if (scheduler == null) {
            // add job to internal list because we will defer adding to the scheduler when camel context has been fully started
            jobsToAdd.add(new JobToAdd(job, trigger));
        } else {
            // add job directly to scheduler
            doAddJob(job, trigger);
        }
    }

    private void doAddJob(JobDetail job, Trigger trigger) throws SchedulerException {
        Trigger existingTrigger = getScheduler().getTrigger(trigger.getName(), trigger.getGroup());
        if (existingTrigger == null) {
            LOG.debug("Adding job using trigger: {}/{}", trigger.getGroup(), trigger.getName());
            getScheduler().scheduleJob(job, trigger);
        } else if (hasTriggerChanged(existingTrigger, trigger)) {
            LOG.debug("Trigger: {}/{} already exists and will be updated by Quartz.", trigger.getGroup(), trigger.getName());
            // fast forward start time to now, as we do not want any misfire to kick in
            trigger.setStartTime(new Date());

            // To ensure trigger uses the same job (the job name might change!) we will remove old trigger then re-add.
            scheduler.unscheduleJob(trigger.getName(), trigger.getGroup());
            scheduler.addJob(job, true);
            trigger.setJobName(job.getName());
            trigger.setJobGroup(job.getGroup());
            scheduler.scheduleJob(trigger);
        } else {
            if (!isClustered()) {
                LOG.debug("Trigger: {}/{} already exists and will be resumed by Quartz.", trigger.getGroup(), trigger.getName());
                // fast forward start time to now, as we do not want any misfire to kick in
                trigger.setStartTime(new Date());

                // To ensure trigger uses the same job (the job name might change!) we will remove old trigger then re-add.
                scheduler.unscheduleJob(trigger.getName(), trigger.getGroup());
                scheduler.addJob(job, true);
                trigger.setJobName(job.getName());
                trigger.setJobGroup(job.getGroup());
                scheduler.scheduleJob(trigger);
            } else {
                LOG.debug("Trigger: {}/{} already exists and is already scheduled by clustered JobStore.", trigger.getGroup(), trigger.getName());
            }
        }

        // only increment job counter if we are successful
        incrementJobCounter(getScheduler());
    }

    private static boolean hasTriggerChanged(Trigger oldTrigger, Trigger newTrigger) {
        if (newTrigger instanceof CronTrigger && oldTrigger instanceof CronTrigger) {
            CronTrigger newCron = (CronTrigger) newTrigger;
            CronTrigger oldCron = (CronTrigger) oldTrigger;
            return !newCron.getCronExpression().equals(oldCron.getCronExpression());
        } else if (newTrigger instanceof SimpleTrigger && oldTrigger instanceof SimpleTrigger) {
            SimpleTrigger newSimple = (SimpleTrigger) newTrigger;
            SimpleTrigger oldSimple = (SimpleTrigger) oldTrigger;
            return newSimple.getRepeatInterval() != oldSimple.getRepeatInterval()
                    || newSimple.getRepeatCount() != oldSimple.getRepeatCount();
        } else {
            return !newTrigger.getClass().equals(oldTrigger.getClass()) || !newTrigger.equals(oldTrigger);
        }
    }

    public void pauseJob(Trigger trigger) throws SchedulerException {
        if (isClustered()) {
            // do not pause jobs which are clustered, as we want the jobs to continue running on the other nodes
            LOG.debug("Cannot pause job using trigger: {}/{} as the JobStore is clustered.", trigger.getGroup(), trigger.getName());
        } else {
            LOG.debug("Pausing job using trigger: {}/{}", trigger.getGroup(), trigger.getName());
            getScheduler().pauseTrigger(trigger.getName(), trigger.getGroup());
            getScheduler().pauseJob(trigger.getName(), trigger.getGroup());
        }

        // only decrement job counter if we are successful
        decrementJobCounter(getScheduler());
    }

    public void deleteJob(String name, String group) throws SchedulerException {
        if (isClustered()) {
            // do not pause jobs which are clustered, as we want the jobs to continue running on the other nodes
            LOG.debug("Cannot delete job using trigger: {}/{} as the JobStore is clustered.", group, name);
        } else {
            Trigger trigger = getScheduler().getTrigger(name, group);
            if (trigger != null) {
                LOG.debug("Deleting job using trigger: {}/{}", group, name);
                getScheduler().unscheduleJob(name, group);
            }
        }
    }

    /**
     * To force shutdown the quartz scheduler
     *
     * @throws SchedulerException can be thrown if error shutting down
     */
    public void shutdownScheduler() throws SchedulerException {
        if (scheduler != null) {
            LOG.info("Forcing shutdown of Quartz scheduler: " + scheduler.getSchedulerName());
            scheduler.shutdown();
            scheduler = null;
        }
    }

    /**
     * Is the quartz scheduler clustered?
     */
    public boolean isClustered() throws SchedulerException {
        try {
            return getScheduler().getMetaData().isJobStoreClustered();
        } catch (NoSuchMethodError e) {
            LOG.debug("Job clustering is only supported since Quartz 1.7, isClustered returning false");
            return false;
        }
    }

    /**
     * To force starting the quartz scheduler
     *
     * @throws SchedulerException can be thrown if error starting
     */
    public void startScheduler() throws SchedulerException {
        for (JobToAdd add : jobsToAdd) {
            doAddJob(add.getJob(), add.getTrigger());
        }
        jobsToAdd.clear();

        if (!getScheduler().isStarted()) {
            if (getStartDelayedSeconds() > 0) {
                LOG.info("Starting Quartz scheduler: " + getScheduler().getSchedulerName() + " delayed: " + getStartDelayedSeconds() + " seconds.");
                try {
                    getScheduler().startDelayed(getStartDelayedSeconds());
                } catch (NoSuchMethodError e) {
                    LOG.warn("Your version of Quartz is too old to support delayed startup! "
                        + "Starting Quartz scheduler immediately : " + getScheduler().getSchedulerName());
                    getScheduler().start();
                }
            } else {
                LOG.info("Starting Quartz scheduler: " + getScheduler().getSchedulerName());
                getScheduler().start();
            }
        }
    }

    // Properties
    // -------------------------------------------------------------------------

    public SchedulerFactory getFactory() throws SchedulerException {
        if (factory == null) {
            factory = createSchedulerFactory();
        }
        return factory;
    }

    /**
     * To use the custom SchedulerFactory which is used to create the Scheduler.
     */
    public void setFactory(SchedulerFactory factory) {
        this.factory = factory;
    }

    public synchronized Scheduler getScheduler() throws SchedulerException {
        if (scheduler == null) {
            scheduler = createScheduler();
        }
        return scheduler;
    }

    /**
     * To use the custom configured Quartz scheduler, instead of creating a new Scheduler.
     */
    public void setScheduler(final Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    public Properties getProperties() {
        return properties;
    }

    /**
     * Properties to configure the Quartz scheduler.
     */
    public void setProperties(Properties properties) {
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

    public int getStartDelayedSeconds() {
        return startDelayedSeconds;
    }

    /**
     * Seconds to wait before starting the quartz scheduler.
     */
    public void setStartDelayedSeconds(int startDelayedSeconds) {
        this.startDelayedSeconds = startDelayedSeconds;
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

    // Implementation methods
    // -------------------------------------------------------------------------

    protected Properties loadProperties() throws SchedulerException {
        Properties answer = getProperties();
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

    protected SchedulerFactory createSchedulerFactory() throws SchedulerException {
        SchedulerFactory answer;

        Properties prop = loadProperties();
        if (prop != null) {

            // force disabling update checker (will do online check over the internet)
            prop.put("org.quartz.scheduler.skipUpdateCheck", "true");

            // camel context name will be a suffix to use one scheduler per context
            String instName = createInstanceName(prop);
            prop.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, instName);

            // enable jmx unless configured to not do so
            if (enableJmx && !prop.containsKey("org.quartz.scheduler.jmx.export")) {
                LOG.info("Setting org.quartz.scheduler.jmx.export=true to ensure QuartzScheduler(s) will be enlisted in JMX.");
                prop.put("org.quartz.scheduler.jmx.export", "true");
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
            String instName = createInstanceName(prop);
            prop.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, instName);

            // force disabling update checker (will do online check over the internet)
            prop.put("org.quartz.scheduler.skipUpdateCheck", "true");

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

    protected Scheduler createScheduler() throws SchedulerException {
        Scheduler scheduler = getFactory().getScheduler();

        if (LOG.isDebugEnabled()) {
            LOG.debug("Using SchedulerFactory {} to get/create Scheduler {}({})",
                    new Object[]{getFactory(), scheduler, ObjectHelper.getIdentityHashCode(scheduler)});
        }

        // register current camel context to scheduler so we can look it up when jobs is being triggered
        // must use management name as it should be unique in the same JVM
        String uid = QuartzHelper.getQuartzContextName(getCamelContext());
        scheduler.getContext().put(QuartzConstants.QUARTZ_CAMEL_CONTEXT + "-" + uid, getCamelContext());

        // store Camel job counter
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get("CamelJobs");
        if (number == null) {
            number = new AtomicInteger(0);
            scheduler.getContext().put("CamelJobs", number);
        }

        return scheduler;
    }

    private static void decrementJobCounter(Scheduler scheduler) throws SchedulerException {
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get("CamelJobs");
        if (number != null) {
            number.decrementAndGet();
        }
    }

    private static void incrementJobCounter(Scheduler scheduler) throws SchedulerException {
        AtomicInteger number = (AtomicInteger) scheduler.getContext().get("CamelJobs");
        if (number != null) {
            number.incrementAndGet();
        }
    }

}
