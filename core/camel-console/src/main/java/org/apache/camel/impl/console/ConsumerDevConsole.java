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

import java.util.Map;

import org.apache.camel.Route;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedConsumerMBean;
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
                    sb.append(String.format("\n    From: %s", mc.getEndpointUri()));
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
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        return root;
    }

}
