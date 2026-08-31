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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "reload", description = "Console for reloading running Camel", readOnly = false)
public class ReloadDevConsole extends AbstractDevConsole {

    public record LastError(
            @Metadata(description = "The error message") String message,
            @Metadata(description = "The error stack trace, one entry per line") List<String> stackTrace) {
    }

    public record ReloadEntry(
            @Metadata(description = "The reload strategy class name") String className,
            @Metadata(description = "Number of successful reloads") int reloaded,
            @Metadata(description = "Number of failed reloads") int failed,
            @Metadata(description = "The last reload error (only present when a reload has failed)") LastError lastError) {
    }

    public record Response(
            @Metadata(description = "The reload strategies (only present when not triggering a reload and at least one reload strategy is active)") List<ReloadEntry> reloadStrategies,
            @Metadata(description = "The reload status, reloading/success/failed (only present when triggering a reload)") String status) {
    }

    @Metadata(label = "query", description = "Option to trigger reloading", javaType = "java.lang.Boolean",
              defaultValue = "false")
    public static final String RELOAD = "reload";

    @Metadata(label = "query", description = "Option to wait for reloading to complete", javaType = "java.lang.Boolean",
              defaultValue = "false")
    public static final String RELOAD_WAIT = "wait";

    // reload on demand should run async to avoid blocking
    private volatile ExecutorService reloadThread;

    public ReloadDevConsole() {
        super("camel", "reload", "Reload", "Console for reloading running Camel");
    }

    protected String doCallText(Map<String, Object> options) {
        boolean trigger = optionBoolean(options, RELOAD, false);
        boolean wait = optionBoolean(options, RELOAD_WAIT, false);
        StringBuilder sb = new StringBuilder();

        Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
        boolean failed = false;
        for (ReloadStrategy r : rs) {
            if (trigger) {
                int before = r.getFailedCounter();
                Future<?> f = getOrCreateReloadTask().submit(() -> r.onReload("ReloadDevConsole"));
                if (wait) {
                    try {
                        f.get(30, TimeUnit.SECONDS);
                        failed |= r.getFailedCounter() > before;
                    } catch (Exception e) {
                        // ignore
                    }
                }
            } else {
                sb.append(String.format("%nReloadStrategy: %s", r.getClass().getName()));
                sb.append(String.format("%n    Reloaded: %s", r.getReloadCounter()));
                sb.append(String.format("%n    Failed: %s", r.getFailedCounter()));
                Exception cause = r.getLastError();
                if (cause != null) {
                    sb.append(String.format("%n    Error Message: %s", cause.getMessage()));
                    final String stackTrace = ExceptionHelper.stackTraceToString(cause);
                    sb.append("\n\n");
                    sb.append(stackTrace);
                    sb.append("\n\n");
                }
            }
        }
        if (trigger) {
            if (wait) {
                if (failed) {
                    sb.append("Status: Reload failed");
                } else {
                    sb.append("Status: Reload success");
                }
            } else {
                sb.append("Status: Reloading in progress");
            }
        }
        sb.append("\n");
        return sb.toString();
    }

    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        boolean trigger = optionBoolean(options, RELOAD, false);
        boolean wait = optionBoolean(options, RELOAD_WAIT, false);

        List<ReloadEntry> reloadStrategies = null;
        String status = null;

        Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
        boolean failed = false;
        for (ReloadStrategy r : rs) {
            if (trigger) {
                int before = r.getFailedCounter();
                Future<?> f = getOrCreateReloadTask().submit(() -> r.onReload("ReloadDevConsole"));
                if (wait) {
                    try {
                        f.get(30, TimeUnit.SECONDS);
                        failed |= r.getFailedCounter() > before;
                    } catch (Exception e) {
                        // ignore
                    }
                }
            } else {
                if (reloadStrategies == null) {
                    reloadStrategies = new ArrayList<>();
                }
                LastError lastError = null;
                Throwable cause = r.getLastError();
                if (cause != null) {
                    final String trace = ExceptionHelper.stackTraceToString(cause);
                    lastError = new LastError(cause.getMessage(), Arrays.asList(trace.split("\n")));
                }
                reloadStrategies.add(new ReloadEntry(
                        r.getClass().getName(), r.getReloadCounter(), r.getFailedCounter(), lastError));
            }
        }

        if (trigger) {
            status = wait ? (failed ? "failed" : "success") : "reloading";
        }

        Response response = new Response(reloadStrategies, status);
        return JsonRecordSupport.toJsonObject(response);
    }

    protected ExecutorService getOrCreateReloadTask() {
        if (reloadThread == null) {
            reloadThread = getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "ReloadOnDemand");
        }
        return reloadThread;
    }

    @Override
    protected void doStop() throws Exception {
        if (reloadThread != null) {
            getCamelContext().getExecutorServiceManager().shutdown(reloadThread);
            reloadThread = null;
        }
    }
}
