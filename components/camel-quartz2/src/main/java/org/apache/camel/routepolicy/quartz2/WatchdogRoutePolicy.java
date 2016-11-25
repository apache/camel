package org.apache.camel.routepolicy.quartz2;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.Exchange;
import org.apache.camel.Route;
import org.apache.camel.impl.MDCUnitOfWork;
import org.apache.camel.support.RoutePolicySupport;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.impl.DirectSchedulerFactory;
import org.quartz.simpl.RAMJobStore;
import org.quartz.simpl.SimpleThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.quartz.CronScheduleBuilder.cronSchedule;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

public class WatchdogRoutePolicy extends RoutePolicySupport {

    public static final String WATCHDOG = "WATCHDOG";

    private static final Logger log = LoggerFactory.getLogger(WatchdogRoutePolicy.class);

    protected final ConcurrentSkipListMap<Long, Exchange> completedExchanges = new ConcurrentSkipListMap<>();
    protected Long firstStartedAndNotCompletedExchangeTimestamp;
    protected Route route;

    private final List<WatcherDefinition> watcherDefinitions = new ArrayList<>();
    private final AtomicBoolean watchersStarted = new AtomicBoolean(false);
    private Integer maxExchanges;
    private Scheduler scheduler;
    private boolean immediate = false;

    public WatchdogRoutePolicy() {
    }

    public WatchdogRoutePolicy(int maxExchanges) {
        this.maxExchanges = maxExchanges;
    }

    @Override
    public void onStart(Route route) {
        this.route = route;
        try {
            createScheduler();
            scheduler.start();
            if (null == maxExchanges) {
                maxExchanges = determineMaxExchanges(watcherDefinitions);
            }
            log.debug("Watchdog will hold last {} exchanges", maxExchanges);
            if (immediate && !watchersStarted.getAndSet(true)) {
                startWatchers(route);
            }
        } catch (SchedulerException e) {
            log.warn("Failed to start the scheduler. This exception is ignored.", e);
        }
    }

    @Override
    public void onStop(Route route) {
        try {
            scheduler.shutdown();
            watchersStarted.set(false);
        } catch (SchedulerException e) {
            log.warn("Failed to stop the scheduler. This exception is ignored.", e);
        }
        this.route = null;
    }

    @Override
    public void onResume(Route route) {
        onStart(route);
    }

    @Override
    public void onSuspend(Route route) {
        onStop(route);
    }

    @Override
    public void onExchangeBegin(Route route, Exchange exchange) {
        if (completedExchanges.isEmpty() || completedExchanges.lastKey() > (firstStartedAndNotCompletedExchangeTimestamp == null ? 0 : firstStartedAndNotCompletedExchangeTimestamp)) {
            firstStartedAndNotCompletedExchangeTimestamp = getTimestamp();
        }
        if (!watchersStarted.getAndSet(true)) { // Start watchers on first exchange if not already started
            startWatchers(route);
        }
    }

    @Override
    public void onExchangeDone(Route route, Exchange exchange) {
        long timestamp = getTimestamp();
        Exchange exchangeCopy = exchange.copy();
        exchangeCopy.getIn().setBody(null); // Drop body to conserve memory
        completedExchanges.put(timestamp, exchangeCopy);
        log.debug("Saved exchange with timestamp: {}", timestamp);

        log.debug("completedExchanges size is: {}", completedExchanges.size());
        if (completedExchanges.size() > maxExchanges) {
            log.debug("Removing oldest exchange with timestamp: {}", completedExchanges.firstKey());
            completedExchanges.remove(completedExchanges.firstKey());
        }
    }

    public WatchdogRoutePolicy addWatcher(WatcherDefinition watcherDefinition) throws SchedulerException {
        watcherDefinitions.add(watcherDefinition);
        return this;
    }

    @SuppressWarnings("unused")
    public WatchdogRoutePolicy immediate() {
        return immediate(true);
    }

    public WatchdogRoutePolicy immediate(boolean immediate) {
        this.immediate = immediate;
        return this;
    }

    public static long getTimestamp() {
        return System.currentTimeMillis();
    }

    private void startWatchers(Route route) {
        String camelContextId = route.getRouteContext().getCamelContext().getName();
        String camelRouteId = route.getId();
        log.debug("Starting watchers");
        for (WatcherDefinition watcherDefinition : watcherDefinitions) {
            try {
                scheduleWatcher(watcherDefinition, camelContextId, camelRouteId);
            } catch (SchedulerException e) {
                log.warn("Failed to start watcher. This exception is ignored.", e);
            }
        }
    }

    private void scheduleWatcher(WatcherDefinition watcherDefinition, String camelContextId, String camelRouteId) throws SchedulerException {
        if (!watcherDefinition.isEnabled()) {
            return;
        }
        JobDetail jobDetail = newJob(Watcher.class)
            .usingJobData(new JobDataMap(watcherDefinition.getConfiguration()))
            .usingJobData(MDCUnitOfWork.MDC_CAMEL_CONTEXT_ID, camelContextId)
            .usingJobData(MDCUnitOfWork.MDC_ROUTE_ID, camelRouteId)
            .build();
        Trigger trigger = newTrigger()
            .startNow()
            .withSchedule(cronSchedule(watcherDefinition.getCronExpression()).withMisfireHandlingInstructionDoNothing())
            .build();
        scheduler.scheduleJob(jobDetail, trigger);
    }

    private void createScheduler() throws SchedulerException {
        SimpleThreadPool threadPool = new SimpleThreadPool(1 + watcherDefinitions.size(), Thread.NORM_PRIORITY);
        threadPool.setThreadsInheritContextClassLoaderOfInitializingThread(true);
        threadPool.initialize();
        DirectSchedulerFactory.getInstance().createScheduler(String.valueOf(hashCode()), String.valueOf(hashCode()), threadPool, new RAMJobStore());
        scheduler = DirectSchedulerFactory.getInstance().getScheduler(String.valueOf(hashCode()));
        scheduler.getContext().put(WATCHDOG, this);
    }

    private int determineMaxExchanges(final List<WatcherDefinition> watcherDefinitions) {
        int max = 0;
        for (WatcherDefinition w : watcherDefinitions) {
            max = Math.max(max, (null != w.getConfiguration().get(WatcherDefinition.MIN_COUNT) ? (int)w.getConfiguration().get(WatcherDefinition.MIN_COUNT) : 0)); // We need space for at least the min count
            max = Math.max(max, (null != w.getConfiguration().get(WatcherDefinition.MAX_COUNT) ? (int)w.getConfiguration().get(WatcherDefinition.MAX_COUNT) + 1 : 0)); // We need enough space to exceed max count
        }
        if (max < 1) {
            return 10; // Default number of exchanges to keep
        } else {
            return max;
        }
    }
}
