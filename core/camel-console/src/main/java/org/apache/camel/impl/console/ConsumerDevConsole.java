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
package org.apache.camel.impl.console;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedConsumerMBean;
import org.apache.camel.api.management.mbean.ManagedRouteMBean;
import org.apache.camel.api.management.mbean.ManagedSchedulePollConsumerMBean;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "consumer", displayName = "Consumers", description = "Display information about Camel consumers")
public class ConsumerDevConsole extends AbstractDevConsole {

    public record Statistics(
            @Metadata(description = "Epoch time in milliseconds since the route has been idle") long idleSince,
            @Metadata(description = "Total number of exchanges") long exchangesTotal,
            @Metadata(description = "Number of failed exchanges") long exchangesFailed,
            @Metadata(description = "Number of inflight exchanges") long exchangesInflight,
            @Metadata(description = "Mean processing time in milliseconds") long meanProcessingTime,
            @Metadata(description = "Max processing time in milliseconds") long maxProcessingTime,
            @Metadata(description = "Min processing time in milliseconds") long minProcessingTime,
            @Metadata(description = "50th percentile processing time in milliseconds (only present when available)") Long p50ProcessingTime,
            @Metadata(description = "95th percentile processing time in milliseconds (only present when available)") Long p95ProcessingTime,
            @Metadata(description = "99th percentile processing time in milliseconds (only present when available)") Long p99ProcessingTime,
            @Metadata(description = "Processing time in milliseconds of the last exchange (only present once an exchange has completed)") Long lastProcessingTime,
            @Metadata(description = "Difference in processing time in milliseconds since the previous exchange (only present once an exchange has completed)") Long deltaProcessingTime,
            @Metadata(description = "Epoch time in milliseconds the last exchange was created (only present once an exchange has been created)") Long lastCreatedExchangeTimestamp,
            @Metadata(description = "Epoch time in milliseconds the last exchange completed (only present once an exchange has completed)") Long lastCompletedExchangeTimestamp,
            @Metadata(description = "Epoch time in milliseconds the last exchange failure was handled (only present once one has occurred)") Long lastFailureHandledExchangeTimestamp,
            @Metadata(description = "Epoch time in milliseconds the last exchange failed (only present once one has occurred)") Long lastFailedExchangeTimestamp) {
    }

    public record ConsumerEntry(
            @Metadata(description = "The route ID") String id,
            @Metadata(description = "The endpoint URI") String uri,
            @Metadata(description = "The consumer state") String state,
            @Metadata(description = "The consumer service type") String clazz,
            @Metadata(description = "Whether the endpoint is remote") boolean remote,
            @Metadata(description = "Whether the consumer is a hosted service") boolean hosted,
            @Metadata(description = "Number of inflight exchanges") int inflight,
            @Metadata(description = "Whether the consumer is scheduled (a scheduled poll consumer or a timer consumer)") boolean scheduled,
            @Metadata(description = "Whether currently polling (only present for scheduled poll consumers)") Boolean polling,
            @Metadata(description = "Whether the first poll has completed (only present for scheduled poll consumers)") Boolean firstPollDone,
            @Metadata(description = "Whether the scheduler has started (only present for scheduled poll consumers)") Boolean schedulerStarted,
            @Metadata(description = "The scheduler class name (only present for scheduled poll consumers)") String schedulerClass,
            @Metadata(description = "The repeat count (only present for scheduled poll consumers)") Long repeatCount,
            @Metadata(description = "Whether a fixed delay is used (only present for scheduled poll consumers)") Boolean fixedDelay,
            @Metadata(description = "The initial delay (only present for scheduled poll consumers)") Long initialDelay,
            @Metadata(description = "The delay (only present for scheduled poll consumers)") Long delay,
            @Metadata(description = "The time unit of the delay (only present for scheduled poll consumers)") String timeUnit,
            @Metadata(description = "Whether greedy scheduling is used (only present for scheduled poll consumers)") Boolean greedy,
            @Metadata(description = "The running logging level (only present for scheduled poll consumers or timer consumers)") String runningLoggingLevel,
            @Metadata(description = "Total number of polls (only present for scheduled poll consumers or timer consumers)") Long totalCounter,
            @Metadata(description = "Number of failed polls (only present for scheduled poll consumers)") Long errorCounter,
            @Metadata(description = "Number of successful polls (only present for scheduled poll consumers)") Long successCounter,
            @Metadata(description = "The backoff counter (only present for scheduled poll consumers)") Long backoffCounter,
            @Metadata(description = "The backoff multiplier (only present for scheduled poll consumers)") Long backoffMultiplier,
            @Metadata(description = "The backoff error threshold (only present for scheduled poll consumers)") Long backoffErrorThreshold,
            @Metadata(description = "The backoff idle threshold (only present for scheduled poll consumers)") Long backoffIdleThreshold,
            @Metadata(description = "The timer name (only present for camel-timer consumers)") String timerName,
            @Metadata(description = "Whether a fixed rate is used (only present for camel-timer consumers)") Boolean fixedRate,
            @Metadata(description = "The period (only present for camel-timer consumers)") Long period,
            @Metadata(description = "Runtime statistics for the route") Statistics statistics) {
    }

    public record Response(@Metadata(description = "The consumers") List<ConsumerEntry> consumers) {
    }

    public ConsumerDevConsole() {
        super("camel", "consumer", "Consumers", "Display information about Camel consumers");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            for (Route route : getCamelContext().getRoutes()) {
                String id = route.getId();
                ManagedConsumerMBean mc = mcc.getManagedConsumer(id);
                if (mc != null) {
                    Integer inflight = mc.getInflightExchanges();
                    if (inflight == null) {
                        inflight = 0;
                    }

                    if (!sb.isEmpty()) {
                        sb.append("\n");
                    }
                    sb.append(String.format("%n    Id: %s", id));
                    sb.append(String.format("%n    Uri: %s", mc.getEndpointUri()));
                    sb.append(String.format("%n    State: %s", mc.getState()));
                    sb.append(String.format("%n    Class: %s", mc.getServiceType()));
                    sb.append(String.format("%n    Remote: %b", mc.isRemoteEndpoint()));
                    sb.append(String.format("%n    Hosted: %b", mc.isHostedService()));
                    sb.append(String.format("%n    Inflight: %d", inflight));
                    if (mcc instanceof ManagedSchedulePollConsumerMBean mpc) {
                        sb.append(String.format("%n    Polling: %s", mpc.isPolling()));
                        sb.append(String.format("%n    First Poll Done: %s", mpc.isFirstPollDone()));
                        sb.append(String.format("%n    Scheduler Started: %s", mpc.isSchedulerStarted()));
                        sb.append(String.format("%n    Scheduler Class: %s", mpc.getSchedulerClassName()));
                        sb.append(String.format("%n    Repeat Count: %s", mpc.getRepeatCount()));
                        sb.append(String.format("%n    Fixed Delay: %s", mpc.isUseFixedDelay()));
                        sb.append(String.format("%n    Greedy: %s", mpc.isGreedy()));
                        sb.append(String.format("%n    Running Logging Level: %s", mpc.getRunningLoggingLevel()));
                        sb.append(String.format("%n    Send Empty Message When Idle: %s", mpc.isSendEmptyMessageWhenIdle()));
                        sb.append(String.format("%n    Counter (total: %d success: %d error: %d)",
                                mpc.getCounter(), mpc.getSuccessCounter(), mpc.getErrorCounter()));
                        sb.append(String.format("%n    Delay (initial: %d delay: %d unit: %s)",
                                mpc.getInitialDelay(), mpc.getDelay(), mpc.getTimeUnit()));
                        sb.append(String.format(
                                "\n    Backoff(counter: %d multiplier: %d errorThreshold: %d, idleThreshold: %d )",
                                mpc.getBackoffCounter(), mpc.getBackoffMultiplier(), mpc.getBackoffErrorThreshold(),
                                mpc.getBackoffIdleThreshold()));
                    }
                    if ("TimerConsumer".equals(mc.getServiceType())) {
                        // need to use JMX to gather details for camel-timer consumer
                        try {
                            MBeanServer ms = ManagementFactory.getPlatformMBeanServer();
                            ObjectName on = getCamelContext().getManagementStrategy().getManagementObjectNameStrategy()
                                    .getObjectNameForConsumer(getCamelContext(),
                                            route.getConsumer());
                            if (ms.isRegistered(on)) {
                                String timerName = (String) ms.getAttribute(on, "TimerName");
                                Long counter = (Long) ms.getAttribute(on, "Counter");
                                Boolean polling = (Boolean) ms.getAttribute(on, "Polling");
                                Boolean fixedRate = (Boolean) ms.getAttribute(on, "FixedRate");
                                Long delay = (Long) ms.getAttribute(on, "Delay");
                                Long period = (Long) ms.getAttribute(on, "Period");
                                Long repeatCount = (Long) ms.getAttribute(on, "RepeatCount");
                                String runLoggingLevel = (String) ms.getAttribute(on, "RunLoggingLevel");

                                sb.append(String.format("%n    Timer Name: %s", timerName));
                                sb.append(String.format("%n    Polling: %s", polling));
                                sb.append(String.format("%n    Fixed Rate: %s", fixedRate));
                                if (delay != null) {
                                    sb.append(String.format("%n    Delay: %s", delay));
                                }
                                if (period != null) {
                                    sb.append(String.format("%n    Period: %s", period));
                                }
                                if (repeatCount != null) {
                                    sb.append(String.format("%n    Repeat Count: %s", repeatCount));
                                }
                                sb.append(String.format("%n    Running Logging Level: %s", runLoggingLevel));
                                sb.append(String.format("%n    Counter (total: %s)", counter));

                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        final List<ConsumerEntry> list = new ArrayList<>();

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            for (Route route : getCamelContext().getRoutes()) {
                String id = route.getId();
                ManagedRouteMBean mr = mcc.getManagedRoute(id);
                ManagedConsumerMBean mc = mcc.getManagedConsumer(id);
                if (mr != null && mc != null) {
                    Integer inflightObj = mc.getInflightExchanges();
                    int inflight = inflightObj != null ? inflightObj : 0;

                    boolean scheduled = false;
                    Boolean polling = null;
                    Boolean firstPollDone = null;
                    Boolean schedulerStarted = null;
                    String schedulerClass = null;
                    Long repeatCount = null;
                    Boolean fixedDelay = null;
                    Long initialDelay = null;
                    Long delay = null;
                    String timeUnit = null;
                    Boolean greedy = null;
                    String runningLoggingLevel = null;
                    Long totalCounter = null;
                    Long errorCounter = null;
                    Long successCounter = null;
                    Long backoffCounter = null;
                    Long backoffMultiplier = null;
                    Long backoffErrorThreshold = null;
                    Long backoffIdleThreshold = null;
                    String timerName = null;
                    Boolean fixedRate = null;
                    Long period = null;

                    // NOTE: this checks mcc (the ManagedCamelContext), not mc (the consumer) - so this branch is
                    // effectively dead code, but that pre-existing behavior is preserved as-is here
                    if (mcc instanceof ManagedSchedulePollConsumerMBean mpc) {
                        scheduled = true;
                        polling = mpc.isPolling();
                        firstPollDone = mpc.isFirstPollDone();
                        schedulerStarted = mpc.isSchedulerStarted();
                        schedulerClass = mpc.getSchedulerClassName();
                        repeatCount = mpc.getRepeatCount();
                        fixedDelay = mpc.isUseFixedDelay();
                        initialDelay = mpc.getInitialDelay();
                        delay = mpc.getDelay();
                        timeUnit = mpc.getTimeUnit();
                        greedy = mpc.isGreedy();
                        runningLoggingLevel = mpc.getRunningLoggingLevel();
                        totalCounter = mpc.getCounter();
                        errorCounter = mpc.getErrorCounter();
                        successCounter = mpc.getSuccessCounter();
                        backoffCounter = (long) mpc.getBackoffCounter();
                        backoffMultiplier = (long) mpc.getBackoffMultiplier();
                        backoffErrorThreshold = (long) mpc.getBackoffErrorThreshold();
                        backoffIdleThreshold = (long) mpc.getBackoffIdleThreshold();
                    }
                    if ("TimerConsumer".equals(mc.getServiceType())) {
                        scheduled = true;
                        // need to use JMX to gather details for camel-timer consumer
                        try {
                            MBeanServer ms = ManagementFactory.getPlatformMBeanServer();
                            ObjectName on = getCamelContext().getManagementStrategy().getManagementObjectNameStrategy()
                                    .getObjectNameForConsumer(getCamelContext(),
                                            route.getConsumer());
                            if (ms.isRegistered(on)) {
                                timerName = (String) ms.getAttribute(on, "TimerName");
                                totalCounter = (Long) ms.getAttribute(on, "Counter");
                                polling = (Boolean) ms.getAttribute(on, "Polling");
                                fixedRate = (Boolean) ms.getAttribute(on, "FixedRate");
                                delay = (Long) ms.getAttribute(on, "Delay");
                                period = (Long) ms.getAttribute(on, "Period");
                                repeatCount = (Long) ms.getAttribute(on, "RepeatCount");
                                runningLoggingLevel = (String) ms.getAttribute(on, "RunLoggingLevel");
                            }
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    Statistics stats = toStatistics(mr);

                    list.add(new ConsumerEntry(
                            id, mc.getEndpointUri(), mc.getState(), mc.getServiceType(), mc.isRemoteEndpoint(),
                            mc.isHostedService(), inflight, scheduled, polling, firstPollDone, schedulerStarted,
                            schedulerClass, repeatCount, fixedDelay, initialDelay, delay, timeUnit, greedy,
                            runningLoggingLevel, totalCounter, errorCounter, successCounter, backoffCounter,
                            backoffMultiplier, backoffErrorThreshold, backoffIdleThreshold, timerName, fixedRate,
                            period, stats));
                }
            }
        }

        Response response = new Response(list);
        return JsonRecordSupport.toJsonObject(response);
    }

    private static Statistics toStatistics(ManagedRouteMBean mr) {
        Long p50 = null;
        Long p95 = null;
        Long p99 = null;
        if (mr.getProcessingTimeP50() >= 0) {
            p50 = mr.getProcessingTimeP50();
            p95 = mr.getProcessingTimeP95();
            p99 = mr.getProcessingTimeP99();
        }

        Long lastProcessingTime = null;
        Long deltaProcessingTime = null;
        if (mr.getExchangesTotal() > 0) {
            lastProcessingTime = mr.getLastProcessingTime();
            deltaProcessingTime = mr.getDeltaProcessingTime();
        }

        Long lastCreated = timestampOf(mr.getLastExchangeCreatedTimestamp());
        Long lastCompleted = timestampOf(mr.getLastExchangeCompletedTimestamp());
        Long lastFailureHandled = timestampOf(mr.getLastExchangeFailureHandledTimestamp());
        Long lastFailed = timestampOf(mr.getLastExchangeFailureTimestamp());

        return new Statistics(
                mr.getIdleSince(), mr.getExchangesTotal(), mr.getExchangesFailed(), mr.getExchangesInflight(),
                mr.getMeanProcessingTime(), mr.getMaxProcessingTime(), mr.getMinProcessingTime(),
                p50, p95, p99, lastProcessingTime, deltaProcessingTime,
                lastCreated, lastCompleted, lastFailureHandled, lastFailed);
    }

    private static Long timestampOf(Date date) {
        return date != null ? date.getTime() : null;
    }

}
