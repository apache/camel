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
package org.apache.camel.support.startup;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.StartupStep;
import org.apache.camel.VetoCamelContextStartException;
import org.apache.camel.spi.StartupCondition;
import org.apache.camel.spi.StartupConditionStrategy;
import org.apache.camel.spi.StartupStepRecorder;
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
    private String classNames;
    private boolean enabled;
    private int interval = 500;
    private int timeout = 20000;
    private String onTimeout = "stop";
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

    public String getOnTimeout() {
        return onTimeout;
    }

    public void setOnTimeout(String onTimeout) {
        this.onTimeout = onTimeout;
    }

    @Override
    public void addStartupCondition(StartupCondition startupCondition) {
        conditions.add(startupCondition);
    }

    @Override
    public void addStartupConditions(String classNames) {
        this.classNames = classNames;
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
                if (classNames != null) {
                    for (String fqn : classNames.split(",")) {
                        fqn = fqn.trim();
                        Class<? extends StartupCondition> clazz
                                = camelContext.getClassResolver().resolveMandatoryClass(fqn, StartupCondition.class);
                        list.add(camelContext.getInjector().newInstance(clazz));
                    }
                }
                list.sort(OrderedComparator.get());

                if (!list.isEmpty()) {
                    StartupStepRecorder recorder = camelContext.getCamelContextExtension().getStartupStepRecorder();
                    StartupStep step = recorder.beginStep(CamelContext.class, camelContext.getCamelContextExtension().getName(),
                            "Check Startup Conditions");
                    doCheckConditions(list);
                    recorder.endStep(step);
                }

            } catch (ClassNotFoundException e) {
                throw RuntimeCamelException.wrapRuntimeCamelException(e);
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

                // break out if Camel are shutting down
                if (isCamelStopping()) {
                    return;
                }

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
            first = false;
            if (ok) {
                return;
            }

            // wait a bit before next loop
            try {
                Thread.sleep(interval);
            } catch (InterruptedException e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Sleep interrupted, are we stopping? {}", isCamelStopping());
                }
                Thread.currentThread().interrupt();
                throw new VetoCamelContextStartException("Sleep interrupted", e, camelContext, false);
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
        if ("fail".equalsIgnoreCase(onTimeout)) {
            throw new VetoCamelContextStartException(error, camelContext, true);
        } else if ("stop".equalsIgnoreCase(onTimeout)) {
            throw new VetoCamelContextStartException(error, camelContext, false);
        } else {
            LOG.warn(error);
            LOG.warn("Camel will continue to startup");
        }
    }

    private boolean isCamelStopping() {
        return camelContext.isStopping();
    }

}
