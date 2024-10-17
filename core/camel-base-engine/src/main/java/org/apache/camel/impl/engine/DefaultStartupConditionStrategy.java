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
package org.apache.camel.impl.engine;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.StartupCondition;
import org.apache.camel.spi.StartupConditionStrategy;
import org.apache.camel.support.OrderedComparator;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default {@link StartupConditionStrategy}.
 */
public class DefaultStartupConditionStrategy extends ServiceSupport implements StartupConditionStrategy, CamelContextAware {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultStartupConditionStrategy.class);

    private CamelContext camelContext;
    private final List<StartupCondition> conditions = new ArrayList<>();
    private boolean enabled;
    private int interval = 500;
    private int timeout = 10000;
    private boolean failOnTimeout = true;
    private volatile boolean checkDone;

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    @Override
    public int getTimeout() {
        return timeout;
    }

    @Override
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    @Override
    public boolean isFailOnTimeout() {
        return failOnTimeout;
    }

    @Override
    public void setFailOnTimeout(boolean failOnTimeout) {
        this.failOnTimeout = failOnTimeout;
    }

    @Override
    public void addStartupCondition(StartupCondition startupCondition) {
        conditions.add(startupCondition);
    }

    @Override
    public List<StartupCondition> getStartupConditions() {
        return conditions;
    }

    @Override
    public void checkStartupConditions() throws VetoCamelContextStartException {
        if (!checkDone && enabled) {
            try {
                var list = new ArrayList<>(conditions);
                list.addAll(camelContext.getRegistry().findByType(StartupCondition.class));
                list.sort(OrderedComparator.get());
                doCheckConditions(list);
            } finally {
                checkDone = true;
            }
        }
    }

    protected void doCheckConditions(List<StartupCondition> conditions) throws VetoCamelContextStartException {
        StopWatch watch = new StopWatch();
        boolean first = true;
        int tick = 1;
        int counter = 1;
        while (watch.taken() < timeout) {
            boolean ok = true;
            for (StartupCondition startup : conditions) {
                if (first) {
                    String msg = startup.getWaitMessage();
                    if (msg != null) {
                        LOG.info(msg);
                    }
                }
                if (ok) {
                    try {
                        LOG.trace("canContinue attempt #{}: {}", counter, startup.getName());
                        ok = startup.canContinue(camelContext);
                        LOG.debug("canContinue attempt #{}: {} -> {}", counter, startup.getName(), ok);
                    } catch (Exception e) {
                        throw new VetoCamelContextStartException(
                                "Startup condition " + startup.getName() + " failed due to: " + e.getMessage(), e,
                                camelContext);
                    }
                }
            }
            if (ok) {
                return;
            }

            first = false;
            // wait a bit before next loop
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            // log waiting but only once per second
            long seconds = watch.taken() / 1000;
            if (seconds > tick) {
                // tick counter
                tick++;
                // log if taking some unexpected time
                if (tick % 2 == 0) {
                    LOG.info("Waited {} for startup conditions to continue...", TimeUtils.printDuration(watch.taken()));
                }
            }
            counter++;
        }

        String error = "Startup condition timeout error";
        for (StartupCondition startup : conditions) {
            String msg = startup.getFailureMessage();
            if (msg != null) {
                error = "Startup condition: " + startup.getName() + " cannot continue due to: " + msg;
            }
        }
        if (isFailOnTimeout()) {
            throw new VetoCamelContextStartException(error, camelContext);
        } else {
            LOG.warn(error);
        }
    }

}
