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
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "consumer", displayName = "Consumers", description = "Display information about Camel consumers")
public class ConsumerDevConsole extends AbstractDevConsole {

    public ConsumerDevConsole() {
        super("camel", "consumer", "Consumers", "Display information about Camel consumers");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc == null) {
            return sb.toString();
        }

        for (Route route : getCamelContext().getRoutes()) {
            String id = route.getId();
            ManagedConsumerMBean mc = mcc.getManagedConsumer(id);
            if (mc == null) {
                continue;
            }

            if (!sb.isEmpty()) {
                sb.append("\n");
            }

            appendBasicConsumerInfoText(sb, id, mc);
            appendScheduledPollConsumerText(sb, mcc);
            appendTimerConsumerText(sb, mc, route);
        }

        return sb.toString();
    }

    private void appendBasicConsumerInfoText(StringBuilder sb, String id, ManagedConsumerMBean mc) {
        int inflight = mc.getInflightExchanges() != null ? mc.getInflightExchanges() : 0;

        sb.append(String.format("%n    Id: %s", id));
        sb.append(String.format("%n    Uri: %s", mc.getEndpointUri()));
        sb.append(String.format("%n    State: %s", mc.getState()));
        sb.append(String.format("%n    Class: %s", mc.getServiceType()));
        sb.append(String.format("%n    Remote: %b", mc.isRemoteEndpoint()));
        sb.append(String.format("%n    Hosted: %b", mc.isHostedService()));
        sb.append(String.format("%n    Inflight: %d", inflight));
    }

    private void appendScheduledPollConsumerText(StringBuilder sb, ManagedCamelContext mcc) {
        if (!(mcc instanceof ManagedSchedulePollConsumerMBean mpc)) {
            return;
        }

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

    private void appendTimerConsumerText(StringBuilder sb, ManagedConsumerMBean mc, Route route) {
        if (!"TimerConsumer".equals(mc.getServiceType())) {
            return;
        }

        try {
            MBeanServer ms = ManagementFactory.getPlatformMBeanServer();
            ObjectName on = getCamelContext().getManagementStrategy().getManagementObjectNameStrategy()
                    .getObjectNameForConsumer(getCamelContext(), route.getConsumer());

            if (!ms.isRegistered(on)) {
                return;
            }

            appendTimerAttributesText(sb, ms, on);
        } catch (Exception e) {
            // ignore
        }
    }

    private void appendTimerAttributesText(StringBuilder sb, MBeanServer ms, ObjectName on) throws Exception {
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

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();
        root.put("consumers", list);

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc == null) {
            return root;
        }

        for (Route route : getCamelContext().getRoutes()) {
            String id = route.getId();
            ManagedRouteMBean mr = mcc.getManagedRoute(id);
            ManagedConsumerMBean mc = mcc.getManagedConsumer(id);

            if (mr == null || mc == null) {
                continue;
            }

            JsonObject jo = buildBasicConsumerJson(id, mc);
            addScheduledPollConsumerJson(jo, mcc);
            addTimerConsumerJson(jo, mc, route);
            jo.put("statistics", toJsonObject(mr));

            list.add(jo);
        }

        return root;
    }

    private JsonObject buildBasicConsumerJson(String id, ManagedConsumerMBean mc) {
        JsonObject jo = new JsonObject();
        int inflight = mc.getInflightExchanges() != null ? mc.getInflightExchanges() : 0;

        jo.put("id", id);
        jo.put("uri", mc.getEndpointUri());
        jo.put("state", mc.getState());
        jo.put("class", mc.getServiceType());
        jo.put("remote", mc.isRemoteEndpoint());
        jo.put("hosted", mc.isHostedService());
        jo.put("inflight", inflight);
        jo.put("scheduled", false);

        return jo;
    }

    private void addScheduledPollConsumerJson(JsonObject jo, ManagedCamelContext mcc) {
        if (!(mcc instanceof ManagedSchedulePollConsumerMBean mpc)) {
            return;
        }

        jo.put("scheduled", true);
        jo.put("polling", mpc.isPolling());
        jo.put("firstPollDone", mpc.isFirstPollDone());
        jo.put("schedulerStarted", mpc.isSchedulerStarted());
        jo.put("schedulerClass", mpc.getSchedulerClassName());
        jo.put("repeatCount", mpc.getRepeatCount());
        jo.put("fixedDelay", mpc.isUseFixedDelay());
        jo.put("initialDelay", mpc.getInitialDelay());
        jo.put("delay", mpc.getDelay());
        jo.put("timeUnit", mpc.getTimeUnit());
        jo.put("greedy", mpc.isGreedy());
        jo.put("runningLoggingLevel", mpc.getRunningLoggingLevel());
        jo.put("totalCounter", mpc.getCounter());
        jo.put("errorCounter", mpc.getErrorCounter());
        jo.put("successCounter", mpc.getSuccessCounter());
        jo.put("backoffCounter", mpc.getBackoffCounter());
        jo.put("backoffMultiplier", mpc.getBackoffMultiplier());
        jo.put("backoffErrorThreshold", mpc.getBackoffErrorThreshold());
        jo.put("backoffIdleThreshold", mpc.getBackoffIdleThreshold());
    }

    private void addTimerConsumerJson(JsonObject jo, ManagedConsumerMBean mc, Route route) {
        if (!"TimerConsumer".equals(mc.getServiceType())) {
            return;
        }

        jo.put("scheduled", true);

        try {
            MBeanServer ms = ManagementFactory.getPlatformMBeanServer();
            ObjectName on = getCamelContext().getManagementStrategy().getManagementObjectNameStrategy()
                    .getObjectNameForConsumer(getCamelContext(), route.getConsumer());

            if (!ms.isRegistered(on)) {
                return;
            }

            addTimerAttributesJson(jo, ms, on);
        } catch (Exception e) {
            // ignore
        }
    }

    private void addTimerAttributesJson(JsonObject jo, MBeanServer ms, ObjectName on) throws Exception {
        String timerName = (String) ms.getAttribute(on, "TimerName");
        Long counter = (Long) ms.getAttribute(on, "Counter");
        Boolean polling = (Boolean) ms.getAttribute(on, "Polling");
        Boolean fixedRate = (Boolean) ms.getAttribute(on, "FixedRate");
        Long delay = (Long) ms.getAttribute(on, "Delay");
        Long period = (Long) ms.getAttribute(on, "Period");
        Long repeatCount = (Long) ms.getAttribute(on, "RepeatCount");
        String runLoggingLevel = (String) ms.getAttribute(on, "RunLoggingLevel");

        jo.put("timerName", timerName);
        jo.put("polling", polling);
        jo.put("fixedRate", fixedRate);
        if (delay != null) {
            jo.put("delay", delay);
        }
        if (period != null) {
            jo.put("period", period);
        }
        if (repeatCount != null) {
            jo.put("repeatCount", repeatCount);
        }
        jo.put("runningLoggingLevel", runLoggingLevel);
        jo.put("totalCounter", counter);
    }

    private static JsonObject toJsonObject(ManagedRouteMBean mr) {
        JsonObject stats = new JsonObject();
        stats.put("idleSince", mr.getIdleSince());
        stats.put("exchangesTotal", mr.getExchangesTotal());
        stats.put("exchangesFailed", mr.getExchangesFailed());
        stats.put("exchangesInflight", mr.getExchangesInflight());
        stats.put("meanProcessingTime", mr.getMeanProcessingTime());
        stats.put("maxProcessingTime", mr.getMaxProcessingTime());
        stats.put("minProcessingTime", mr.getMinProcessingTime());
        if (mr.getExchangesTotal() > 0) {
            stats.put("lastProcessingTime", mr.getLastProcessingTime());
            stats.put("deltaProcessingTime", mr.getDeltaProcessingTime());
        }
        Date last = mr.getLastExchangeCreatedTimestamp();
        if (last != null) {
            stats.put("lastCreatedExchangeTimestamp", last.getTime());
        }
        last = mr.getLastExchangeCompletedTimestamp();
        if (last != null) {
            stats.put("lastCompletedExchangeTimestamp", last.getTime());
        }
        last = mr.getLastExchangeFailureTimestamp();
        if (last != null) {
            stats.put("lastFailedExchangeTimestamp", last.getTime());
        }
        return stats;
    }

}
