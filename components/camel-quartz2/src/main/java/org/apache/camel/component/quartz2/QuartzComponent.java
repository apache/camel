package org.apache.camel.component.quartz2;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.StartupListener;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

public class QuartzComponent extends DefaultComponent implements StartupListener {
    private static final transient Logger LOG = LoggerFactory.getLogger(QuartzComponent.class);
    private SchedulerFactory schedulerFactory;
    private Scheduler scheduler;
    private Properties properties;
    private String propertiesFile;
    private int startDelayedSeconds;
    private boolean autoStartScheduler = true;

    public QuartzComponent() {
    }

    public QuartzComponent(CamelContext camelContext) {
        super(camelContext);
    }

    public int getStartDelayedSeconds() {
        return startDelayedSeconds;
    }

    public boolean isAutoStartScheduler() {
        return autoStartScheduler;
    }

    public void setStartDelayedSeconds(int startDelayedSeconds) {
        this.startDelayedSeconds = startDelayedSeconds;
    }

    public void setAutoStartScheduler(boolean autoStartScheduler) {
        this.autoStartScheduler = autoStartScheduler;
    }

    public Properties getProperties() {
        return properties;
    }

    public String getPropertiesFile() {
        return propertiesFile;
    }

    public void setProperties(Properties properties) {
        this.properties = properties;
    }

    public void setPropertiesFile(String propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public SchedulerFactory getSchedulerFactory() throws SchedulerException {
        if (schedulerFactory == null) {
            schedulerFactory = createSchedulerFactory();
        }
        return schedulerFactory;
    }

    private SchedulerFactory createSchedulerFactory() throws SchedulerException {
        SchedulerFactory answer;

        Properties prop = loadProperties();
        if (prop != null) {

            // force disabling update checker (will do online check over the internet)
            prop.put("org.quartz.scheduler.skipUpdateCheck", "true");

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
            }

            // camel context name will be a suffix to use one scheduler per context
            String identity = getCamelContext().getManagementName();

            String instName = prop.getProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME);
            if (instName == null) {
                instName = "scheduler-" + identity;
            } else {
                instName = instName + "-" + identity;
            }
            prop.setProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, instName);

            // force disabling update checker (will do online check over the internet)
            prop.put("org.quartz.scheduler.skipUpdateCheck", "true");

            answer = new StdSchedulerFactory(prop);
        }

        if (LOG.isDebugEnabled()) {
            String name = prop.getProperty(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME);
            LOG.debug("Creating SchedulerFactory: {} with properties: {}", name, prop);
        }
        return answer;
    }

    private Properties loadProperties() throws SchedulerException {
        Properties answer = getProperties();
        if (answer == null && getPropertiesFile() != null) {
            LOG.info("Loading Quartz properties file from classpath: {}", getPropertiesFile());
            InputStream is = getCamelContext().getClassResolver().loadResourceAsStream(getPropertiesFile());
            if (is == null) {
                throw new SchedulerException("Quartz properties file not found in classpath: " + getPropertiesFile());
            }
            answer = new Properties();
            try {
                answer.load(is);
            } catch (IOException e) {
                throw new SchedulerException("Error loading Quartz properties file from classpath: " + getPropertiesFile(), e);
            }
        }
        return answer;
    }

    public void setSchedulerFactory(SchedulerFactory schedulerFactory) {
        this.schedulerFactory = schedulerFactory;
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

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
        if (autoStartScheduler != null)
            this.autoStartScheduler = autoStartScheduler;

        // Extract trigger.XXX and job.XXX properties to be set on endpoint below
        Map<String, Object> triggerParameters = IntrospectionSupport.extractProperties(parameters, "trigger.");
        Map<String, Object> jobParameters = IntrospectionSupport.extractProperties(parameters, "job.");

        // Create quartz endpoint
        TriggerKey triggerKey = createTriggerKey(uri, remaining);
        QuartzEndpoint result = new QuartzEndpoint(uri, this);
        result.setTriggerKey(triggerKey);
        result.setTriggerParameters(triggerParameters);
        result.setJobParameters(jobParameters);
        return result;
    }

    private TriggerKey createTriggerKey(String uri, String remaining) throws Exception {
        // Parse uri for trigger name and group
        URI u = new URI(uri);
        String path = ObjectHelper.after(u.getPath(), "/");
        String host = u.getHost();

        // host can be null if the uri did contain invalid host characters such as an underscore
        if (host == null) {
            host = ObjectHelper.before(remaining, "/");
        }

        // Trigger group can be optional, if so set it to Camel
        String name, group;
        if (ObjectHelper.isNotEmpty(path) && ObjectHelper.isNotEmpty(host)) {
            group = host;
            name = path;
        } else {
            group = "Camel";
            name = host;
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

        // Store CamelContext into QuartzContext space
        SchedulerContext quartzContext = scheduler.getContext();
        String camelContextName = getCamelContext().getManagementName();
        LOG.debug("Storing camelContextName={} into Quartz Context space.", camelContextName);
        quartzContext.put(QuartzConstants.QUARTZ_CAMEL_CONTEXT + "-" + camelContextName, getCamelContext());

        // Set camel job counts to zero. We needed this to prevent shutdown in case there are multiple Camel contexts
        // that has not completed yet, and the last one with job counts to zero will eventually shutdown.
        AtomicInteger number = (AtomicInteger) quartzContext.get(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT);
        if (number == null) {
            number = new AtomicInteger(0);
            quartzContext.put(QuartzConstants.QUARTZ_CAMEL_JOBS_COUNT, number);
        }
    }

    private Scheduler createScheduler() throws SchedulerException {
        return getSchedulerFactory().getScheduler();
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        if (scheduler != null) {
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

    @Override
    public void onCamelContextStarted(CamelContext context, boolean alreadyStarted) throws Exception {
        // If Camel has already started and then user add a route dynamically, we need to ensure
        // to create and init the scheduler first.
        if (scheduler == null) {
            createAndInitScheduler();
        }

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
