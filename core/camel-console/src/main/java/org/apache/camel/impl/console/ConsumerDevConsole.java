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

@DevConsole("consumer")
public class ConsumerDevConsole extends AbstractDevConsole {

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
                    sb.append(String.format("\n    Id: %s", id));
                    sb.append(String.format("\n    Uri: %s", mc.getEndpointUri()));
                    sb.append(String.format("\n    State: %s", mc.getState()));
                    sb.append(String.format("\n    Class: %s", mc.getServiceType()));
                    sb.append(String.format("\n    Inflight: %d", inflight));
                    if (mcc instanceof ManagedSchedulePollConsumerMBean mpc) {
                        sb.append(String.format("\n    Polling: %s", mpc.isPolling()));
                        sb.append(String.format("\n    First Poll Done: %s", mpc.isFirstPollDone()));
                        sb.append(String.format("\n    Scheduler Started: %s", mpc.isSchedulerStarted()));
                        sb.append(String.format("\n    Scheduler Class: %s", mpc.getSchedulerClassName()));
                        sb.append(String.format("\n    Repeat Count: %s", mpc.getRepeatCount()));
                        sb.append(String.format("\n    Fixed Delay: %s", mpc.isUseFixedDelay()));
                        sb.append(String.format("\n    Greedy: %s", mpc.isGreedy()));
                        sb.append(String.format("\n    Running Logging Level: %s", mpc.getRunningLoggingLevel()));
                        sb.append(String.format("\n    Send Empty Message When Idle: %s", mpc.isSendEmptyMessageWhenIdle()));
                        sb.append(String.format("\n    Counter(total: %d success: %d error: %d)",
                                mpc.getCounter(), mpc.getSuccessCounter(), mpc.getErrorCounter()));
                        sb.append(String.format("\n    Delay(initial: %d delay: %d unit: %s)",
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

                                sb.append(String.format("\n    Timer Name: %s", timerName));
                                sb.append(String.format("\n    Polling: %s", polling));
                                sb.append(String.format("\n    Fixed Rate: %s", fixedRate));
                                if (delay != null) {
                                    sb.append(String.format("\n    Delay: %s", delay));
                                }
                                if (period != null) {
                                    sb.append(String.format("\n    Period: %s", period));
                                }
                                if (repeatCount != null) {
                                    sb.append(String.format("\n    Repeat Count: %s", repeatCount));
                                }
                                sb.append(String.format("\n    Running Logging Level: %s", runLoggingLevel));
                                sb.append(String.format("\n    Counter(total: %s)", counter));

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
    protected JsonObject doCallJson(Map<String, Object> options) {
        final JsonObject root = new JsonObject();
        final List<JsonObject> list = new ArrayList<>();
        root.put("consumers", list);

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            for (Route route : getCamelContext().getRoutes()) {
                String id = route.getId();
                ManagedRouteMBean mr = mcc.getManagedRoute(id);
                ManagedConsumerMBean mc = mcc.getManagedConsumer(id);
                if (mr != null && mc != null) {
                    JsonObject jo = new JsonObject();
                    Integer inflight = mc.getInflightExchanges();
                    if (inflight == null) {
                        inflight = 0;
                    }

                    jo.put("id", id);
                    jo.put("uri", mc.getEndpointUri());
                    jo.put("state", mc.getState());
                    jo.put("class", mc.getServiceType());
                    jo.put("inflight", inflight);
                    jo.put("scheduled", false);
                    if (mcc instanceof ManagedSchedulePollConsumerMBean mpc) {
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
                    if ("TimerConsumer".equals(mc.getServiceType())) {
                        jo.put("scheduled", true);
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
                        } catch (Exception e) {
                            // ignore
                        }
                    }

                    if (mr != null) {
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
                        jo.put("statistics", stats);
                    }

                    list.add(jo);
                }
            }
        }

        return root;
    }

}
