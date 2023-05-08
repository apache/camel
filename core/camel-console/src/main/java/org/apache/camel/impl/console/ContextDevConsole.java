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

import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonObject;

@DevConsole("context")
public class ContextDevConsole extends AbstractDevConsole {

    public ContextDevConsole() {
        super("camel", "context", "CamelContext", "Overall information about the CamelContext");
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        sb.append(String.format("Apache Camel %s %s (%s) uptime %s", getCamelContext().getVersion(),
                getCamelContext().getStatus().name().toLowerCase(Locale.ROOT), getCamelContext().getName(),
                getCamelContext().getUptime()));
        if (getCamelContext().getDescription() != null) {
            sb.append(String.format("\n    %s", getCamelContext().getDescription()));
        }
        sb.append("\n");

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            ManagedCamelContextMBean mb = mcc.getManagedCamelContext();
            if (mb != null) {
                int reloaded = 0;
                Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
                for (ReloadStrategy r : rs) {
                    reloaded += r.getReloadCounter();
                }
                String load1 = getLoad1(mb);
                String load5 = getLoad5(mb);
                String load15 = getLoad15(mb);
                if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                    sb.append(String.format("\n    Load Average: %s %s %s\n", load1, load5, load15));
                }
                String thp = getThroughput(mb);
                if (!thp.isEmpty()) {
                    sb.append(String.format("\n    Messages/Sec: %s", thp));
                }
                sb.append(String.format("\n    Total: %s", mb.getExchangesTotal()));
                sb.append(String.format("\n    Failed: %s", mb.getExchangesFailed()));
                sb.append(String.format("\n    Inflight: %s", mb.getExchangesInflight()));
                sb.append(String.format("\n    Reloaded: %s", reloaded));
                sb.append(String.format("\n    Mean Time: %s", TimeUtils.printDuration(mb.getMeanProcessingTime(), true)));
                sb.append(String.format("\n    Max Time: %s", TimeUtils.printDuration(mb.getMaxProcessingTime(), true)));
                sb.append(String.format("\n    Min Time: %s", TimeUtils.printDuration(mb.getMinProcessingTime(), true)));
                if (mb.getExchangesTotal() > 0) {
                    sb.append(String.format("\n    Last Time: %s", TimeUtils.printDuration(mb.getLastProcessingTime(), true)));
                    sb.append(
                            String.format("\n    Delta Time: %s", TimeUtils.printDuration(mb.getDeltaProcessingTime(), true)));
                }
                Date last = mb.getLastExchangeCreatedTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    sb.append(String.format("\n    Since Last Started: %s", ago));
                }
                last = mb.getLastExchangeCompletedTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    sb.append(String.format("\n    Since Last Completed: %s", ago));
                }
                last = mb.getLastExchangeFailureTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    sb.append(String.format("\n    Since Last Failed: %s", ago));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();
        root.put("name", getCamelContext().getName());
        if (getCamelContext().getDescription() != null) {
            root.put("description", getCamelContext().getDescription());
        }
        root.put("version", getCamelContext().getVersion());
        root.put("state", getCamelContext().getStatus().name());
        root.put("phase", getCamelContext().getCamelContextExtension().getStatusPhase());
        root.put("uptime", getCamelContext().getUptime());

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            ManagedCamelContextMBean mb = mcc.getManagedCamelContext();
            if (mb != null) {
                JsonObject stats = new JsonObject();

                int reloaded = 0;
                Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
                for (ReloadStrategy r : rs) {
                    reloaded += r.getReloadCounter();
                }
                String load1 = getLoad1(mb);
                String load5 = getLoad5(mb);
                String load15 = getLoad15(mb);
                if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                    stats.put("load01", load1);
                    stats.put("load05", load5);
                    stats.put("load15", load15);
                }
                String thp = getThroughput(mb);
                if (!thp.isEmpty()) {
                    stats.put("exchangesThroughput", thp);
                }
                stats.put("exchangesTotal", mb.getExchangesTotal());
                stats.put("exchangesFailed", mb.getExchangesFailed());
                stats.put("exchangesInflight", mb.getExchangesInflight());
                stats.put("reloaded", reloaded);
                stats.put("meanProcessingTime", mb.getMeanProcessingTime());
                stats.put("maxProcessingTime", mb.getMaxProcessingTime());
                stats.put("minProcessingTime", mb.getMinProcessingTime());
                if (mb.getExchangesTotal() > 0) {
                    stats.put("lastProcessingTime", mb.getLastProcessingTime());
                    stats.put("deltaProcessingTime", mb.getDeltaProcessingTime());
                }
                Date last = mb.getLastExchangeCreatedTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    stats.put("sinceLastCreatedExchange", ago);
                }
                last = mb.getLastExchangeCompletedTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    stats.put("sinceLastCompletedExchange", ago);
                }
                last = mb.getLastExchangeFailureTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    stats.put("sinceLastFailedExchange", ago);
                }
                root.put("statistics", stats);
            }
        }

        return root;
    }

    private String getLoad1(ManagedCamelContextMBean mb) {
        String s = mb.getLoad01();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getLoad5(ManagedCamelContextMBean mb) {
        String s = mb.getLoad05();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getLoad15(ManagedCamelContextMBean mb) {
        String s = mb.getLoad15();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

    private String getThroughput(ManagedCamelContextMBean mb) {
        String s = mb.getThroughput();
        // lets use dot as separator
        s = s.replace(',', '.');
        return s;
    }

}
