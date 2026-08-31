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

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.camel.ContextEvents;
import org.apache.camel.api.management.ManagedCamelContext;
import org.apache.camel.api.management.mbean.ManagedCamelContextMBean;
import org.apache.camel.clock.Clock;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.ResourceReloadStrategy;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.CamelContextHelper;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.TimeUtils;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "context", displayName = "CamelContext", description = "Overall information about the CamelContext")
public class ContextDevConsole extends AbstractDevConsole {

    public record LastError(
            @Metadata(description = "The error message") String message,
            @Metadata(description = "The error stack trace, one entry per line") List<String> stackTrace) {
    }

    public record Reload(
            @Metadata(description = "Number of successful reloads") int reloaded,
            @Metadata(description = "Number of failed reloads") int failed,
            @Metadata(description = "The last reload error (only present when a reload has failed)") LastError lastError) {
    }

    public record Statistics(
            @Metadata(description = "Total number of routes") int routesTotal,
            @Metadata(description = "Number of started routes") int routesStarted,
            @Metadata(description = "1 minute load average (only present when available)") String load01,
            @Metadata(description = "5 minute load average (only present when available)") String load05,
            @Metadata(description = "15 minute load average (only present when available)") String load15,
            @Metadata(description = "Messages per second throughput (only present when available)") String exchangesThroughput,
            @Metadata(description = "Epoch time in milliseconds since the context has been idle") long idleSince,
            @Metadata(description = "Total number of exchanges") long exchangesTotal,
            @Metadata(description = "Number of failed exchanges") long exchangesFailed,
            @Metadata(description = "Number of inflight exchanges") long exchangesInflight,
            @Metadata(description = "Total number of remote exchanges") long remoteExchangesTotal,
            @Metadata(description = "Number of failed remote exchanges") long remoteExchangesFailed,
            @Metadata(description = "Number of inflight remote exchanges") long remoteExchangesInflight,
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
            @Metadata(description = "Epoch time in milliseconds the last exchange failed (only present once one has occurred)") Long lastFailedExchangeTimestamp,
            @Metadata(description = "Reload statistics") Reload reload) {
    }

    public record Response(
            @Metadata(description = "The CamelContext name") String name,
            @Metadata(description = "The CamelContext description (only present when configured)") String description,
            @Metadata(description = "The active profile (only present when configured)") String profile,
            @Metadata(description = "The Camel version") String version,
            @Metadata(description = "The CamelContext state") String state,
            @Metadata(description = "The CamelContext lifecycle phase") int phase,
            @Metadata(description = "Epoch time in milliseconds the CamelContext was started (only present once started)") Long startTimestamp,
            @Metadata(description = "Uptime in milliseconds") long uptime,
            @Metadata(description = "Whether dev mode (live reload) is active") boolean devMode,
            @Metadata(description = "Runtime statistics (only present when management is enabled)") Statistics statistics) {
    }

    public ContextDevConsole() {
        super("camel", "context", "CamelContext", "Overall information about the CamelContext");
    }

    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        String profile = "";
        if (getCamelContext().getCamelContextExtension().getProfile() != null) {
            profile = " (profile: " + getCamelContext().getCamelContextExtension().getProfile() + ")";
        }
        sb.append(String.format("Apache Camel %s %s (%s)%s uptime %s", getCamelContext().getVersion(),
                getCamelContext().getStatus().name().toLowerCase(Locale.ROOT), getCamelContext().getName(),
                profile, CamelContextHelper.getUptime(getCamelContext())));
        if (getCamelContext().getDescription() != null) {
            sb.append(String.format("%n    %s", getCamelContext().getDescription()));
        }
        Clock startClock = getCamelContext().getClock().get(ContextEvents.START);
        if (startClock != null) {
            sb.append(String.format("%n    Started: %s", startClock.asDate()));
        }
        sb.append("\n");

        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            ManagedCamelContextMBean mb = mcc.getManagedCamelContext();
            if (mb != null) {
                int total = mb.getTotalRoutes();
                int started = mb.getStartedRoutes();
                sb.append(String.format("%n    Routes: %s (started: %s)", total, started));

                int reloaded = 0;
                int reloadedFailed = 0;
                Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
                for (ReloadStrategy r : rs) {
                    reloaded += r.getReloadCounter();
                    reloadedFailed += r.getFailedCounter();
                }
                String load1 = getLoad1(mb);
                String load5 = getLoad5(mb);
                String load15 = getLoad15(mb);
                if (!load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty()) {
                    sb.append(String.format("%n    Load Average: %s %s %s", load1, load5, load15));
                }
                String thp = getThroughput(mb);
                if (!thp.isEmpty()) {
                    sb.append(String.format("%n    Messages/Sec: %s", thp));
                }
                sb.append(String.format("%n    Total: %s/%s", mb.getRemoteExchangesTotal(), mb.getExchangesTotal()));
                sb.append(String.format("%n    Failed: %s/%s", mb.getRemoteExchangesFailed(), mb.getExchangesFailed()));
                sb.append(String.format("%n    Inflight: %s/%s", mb.getRemoteExchangesInflight(), mb.getExchangesInflight()));
                long idle = mb.getIdleSince();
                if (idle > 0) {
                    sb.append(String.format("%n    Idle Since: %s", TimeUtils.printDuration(idle)));
                } else {
                    sb.append(String.format("%n    Idle Since: %s", ""));
                }
                sb.append(String.format("%n    Reloaded: %s/%s", reloaded, reloadedFailed));
                boolean devMode = getCamelContext().hasService(ResourceReloadStrategy.class) != null;
                sb.append(String.format("%n    Dev Mode: %s", devMode));
                sb.append(String.format("%n    Mean Time: %s", TimeUtils.printDuration(mb.getMeanProcessingTime(), true)));
                sb.append(String.format("%n    Max Time: %s", TimeUtils.printDuration(mb.getMaxProcessingTime(), true)));
                sb.append(String.format("%n    Min Time: %s", TimeUtils.printDuration(mb.getMinProcessingTime(), true)));
                if (mb.getExchangesTotal() > 0) {
                    sb.append(String.format("%n    Last Time: %s", TimeUtils.printDuration(mb.getLastProcessingTime(), true)));
                    sb.append(
                            String.format("%n    Delta Time: %s", TimeUtils.printDuration(mb.getDeltaProcessingTime(), true)));
                }
                Date last = mb.getLastExchangeCreatedTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    sb.append(String.format("%n    Since Last Started: %s", ago));
                }
                last = mb.getLastExchangeCompletedTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    sb.append(String.format("%n    Since Last Completed: %s", ago));
                }
                last = mb.getLastExchangeFailureHandledTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    sb.append(String.format("%n    Since Last Failure Handled: %s", ago));
                }
                last = mb.getLastExchangeFailureTimestamp();
                if (last != null) {
                    String ago = TimeUtils.printSince(last.getTime());
                    sb.append(String.format("%n    Since Last Failed: %s", ago));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        String description = getCamelContext().getDescription();
        String profile = getCamelContext().getCamelContextExtension().getProfile();
        long uptimeMillis = getCamelContext().getUptime().toMillis();
        Clock startClock = getCamelContext().getClock().get(ContextEvents.START);
        Long startTimestamp = startClock != null ? startClock.getCreated() : null;
        int phase = getCamelContext().getCamelContextExtension().getStatusPhase();
        boolean devMode = getCamelContext().hasService(ResourceReloadStrategy.class) != null;

        Statistics stats = null;
        ManagedCamelContext mcc = getCamelContext().getCamelContextExtension().getContextPlugin(ManagedCamelContext.class);
        if (mcc != null) {
            ManagedCamelContextMBean mb = mcc.getManagedCamelContext();
            if (mb != null) {
                stats = buildStatistics(mb);
            }
        }

        Response response = new Response(
                getCamelContext().getName(), description, profile, getCamelContext().getVersion(),
                getCamelContext().getStatus().name(), phase, startTimestamp, uptimeMillis, devMode, stats);
        return JsonRecordSupport.toJsonObject(response);
    }

    private Statistics buildStatistics(ManagedCamelContextMBean mb) {
        String load1 = getLoad1(mb);
        String load5 = getLoad5(mb);
        String load15 = getLoad15(mb);
        boolean hasLoad = !load1.isEmpty() || !load5.isEmpty() || !load15.isEmpty();

        String thp = getThroughput(mb);

        Long p50 = null;
        Long p95 = null;
        Long p99 = null;
        if (mb.getProcessingTimeP50() >= 0) {
            p50 = mb.getProcessingTimeP50();
            p95 = mb.getProcessingTimeP95();
            p99 = mb.getProcessingTimeP99();
        }

        Long lastProcessingTime = null;
        Long deltaProcessingTime = null;
        if (mb.getExchangesTotal() > 0) {
            lastProcessingTime = mb.getLastProcessingTime();
            deltaProcessingTime = mb.getDeltaProcessingTime();
        }

        Long lastCreated = timestampOf(mb.getLastExchangeCreatedTimestamp());
        Long lastCompleted = timestampOf(mb.getLastExchangeCompletedTimestamp());
        Long lastFailureHandled = timestampOf(mb.getLastExchangeFailureHandledTimestamp());
        Long lastFailed = timestampOf(mb.getLastExchangeFailureTimestamp());

        // reload stats
        int reloaded = 0;
        int reloadedFailed = 0;
        Exception reloadCause = null;
        Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
        for (ReloadStrategy r : rs) {
            reloaded += r.getReloadCounter();
            reloadedFailed += r.getFailedCounter();
            if (reloadCause == null) {
                reloadCause = r.getLastError();
            }
        }
        LastError lastError = null;
        if (reloadCause != null) {
            final String trace = ExceptionHelper.stackTraceToString(reloadCause);
            lastError = new LastError(reloadCause.getMessage(), Arrays.asList(trace.split("\n")));
        }
        Reload reload = new Reload(reloaded, reloadedFailed, lastError);

        return new Statistics(
                mb.getTotalRoutes(), mb.getStartedRoutes(),
                hasLoad ? load1 : null, hasLoad ? load5 : null, hasLoad ? load15 : null,
                thp.isEmpty() ? null : thp,
                mb.getIdleSince(), mb.getExchangesTotal(), mb.getExchangesFailed(), mb.getExchangesInflight(),
                mb.getRemoteExchangesTotal(), mb.getRemoteExchangesFailed(), mb.getRemoteExchangesInflight(),
                mb.getMeanProcessingTime(), mb.getMaxProcessingTime(), mb.getMinProcessingTime(),
                p50, p95, p99, lastProcessingTime, deltaProcessingTime,
                lastCreated, lastCompleted, lastFailureHandled, lastFailed, reload);
    }

    private static Long timestampOf(Date date) {
        return date != null ? date.getTime() : null;
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
