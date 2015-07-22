package org.apache.camel.routepolicy.quartz2;

import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.camel.Exchange;
import org.apache.camel.impl.MDCUnitOfWork;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class Watcher implements Job {

    private final static Logger log = LoggerFactory.getLogger(Watcher.class);

    private SortedMap<Long, Exchange> window = new ConcurrentSkipListMap<>();

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        long timestamp = WatchdogRoutePolicy.getTimestamp();
        JobDataMap jobDataMap = context.getMergedJobDataMap();

        // Set up MDC logging
        MDC.put(MDCUnitOfWork.MDC_CAMEL_CONTEXT_ID, jobDataMap.getString(MDCUnitOfWork.MDC_CAMEL_CONTEXT_ID));
        MDC.put(MDCUnitOfWork.MDC_ROUTE_ID, jobDataMap.getString(MDCUnitOfWork.MDC_ROUTE_ID));

        try {
            final WatchdogRoutePolicy watchdog = (WatchdogRoutePolicy) context.getScheduler().getContext().get(WatchdogRoutePolicy.WATCHDOG);
            log.debug("Watchdog has {} exchanges", watchdog.completedExchanges.size());

            // Get exchanges in the time window
            if (null != watchdog.completedExchanges.higherKey(timestamp - jobDataMap.getLong(WatcherDefinition.OFFSET_MILLIS))) {
                window = watchdog.completedExchanges.tailMap(watchdog.completedExchanges.higherKey(timestamp - jobDataMap.getLong(WatcherDefinition.OFFSET_MILLIS)));
            }
            log.debug("Time window has {} exchanges", window.size());
            // Get inflight exchange count
            final int inflightCount = watchdog.route.getRouteContext().getCamelContext().getInflightRepository().size(watchdog.route.getId());
            log.debug("Inflight exchange count is {}", inflightCount);

            // Perform the checks
            String logPrefix = (null != jobDataMap.getString(WatcherDefinition.NAME) ? jobDataMap.getString(WatcherDefinition.NAME) + ": " : "");
            if (jobDataMap.containsKey(WatcherDefinition.MIN_COUNT) && jobDataMap.getInt(WatcherDefinition.MIN_COUNT) > window.size()) {
                log.warn(logPrefix + "Too few exchanges ({}) processed in {} millis", window.size(), jobDataMap.getLong(WatcherDefinition.OFFSET_MILLIS));
            }
            if (jobDataMap.containsKey(WatcherDefinition.MAX_COUNT) && jobDataMap.getInt(WatcherDefinition.MAX_COUNT) < window.size()) {
                log.warn(logPrefix + "Too many exchanges (at least {}) processed in {} millis", window.size(), jobDataMap.getLong(WatcherDefinition.OFFSET_MILLIS));
            }
            if (inflightCount > 0 && window.isEmpty() && watchdog.firstStartedAndNotCompletedExchangeTimestamp != null && watchdog.firstStartedAndNotCompletedExchangeTimestamp < timestamp - jobDataMap.getLong(WatcherDefinition.OFFSET_MILLIS)) {
                log.warn(logPrefix + "Exchanges inflight ({}) but none completed within {} millis (stuck route?)", inflightCount, jobDataMap.getLong(WatcherDefinition.OFFSET_MILLIS));
            }
        } catch (SchedulerException e) {
            e.printStackTrace();
        }
    }
}
