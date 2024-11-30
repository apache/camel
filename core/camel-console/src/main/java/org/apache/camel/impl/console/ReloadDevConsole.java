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

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.ExceptionHelper;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole(name = "reload", description = "Console for reloading running Camel")
public class ReloadDevConsole extends AbstractDevConsole {

    /**
     * Option to trigger reloading
     */
    public static final String RELOAD = "reload";

    /**
     * Option to wait for reloading to complete
     */
    public static final String RELOAD_WAIT = "wait";

    // reload on demand should run async to avoid blocking
    private volatile ExecutorService reloadThread;

    public ReloadDevConsole() {
        super("camel", "reload", "Reload", "Console for reloading running Camel");
    }

    protected String doCallText(Map<String, Object> options) {
        boolean trigger = "true".equals(options.getOrDefault(RELOAD, "false"));
        boolean wait = "true".equals(options.getOrDefault(RELOAD_WAIT, "false"));
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
                sb.append(String.format("\nReloadStrategy: %s", r.getClass().getName()));
                sb.append(String.format("\n    Reloaded: %s", r.getReloadCounter()));
                sb.append(String.format("\n    Failed: %s", r.getFailedCounter()));
                Exception cause = r.getLastError();
                if (cause != null) {
                    sb.append(String.format("\n    Error Message: %s", cause.getMessage()));
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

    protected JsonObject doCallJson(Map<String, Object> options) {
        boolean trigger = "true".equals(options.getOrDefault(RELOAD, "false"));
        boolean wait = "true".equals(options.getOrDefault(RELOAD_WAIT, "false"));
        JsonObject root = new JsonObject();

        JsonArray arr = new JsonArray();
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
                if (root.isEmpty()) {
                    root.put("reloadStrategies", arr);
                }
                JsonObject jo = new JsonObject();
                arr.add(jo);
                jo.put("className", r.getClass().getName());
                jo.put("reloaded", r.getReloadCounter());
                jo.put("failed", r.getFailedCounter());
                Throwable cause = r.getLastError();
                if (cause != null) {
                    JsonObject eo = new JsonObject();
                    eo.put("message", cause.getMessage());
                    JsonArray arr2 = new JsonArray();
                    final String trace = ExceptionHelper.stackTraceToString(cause);
                    eo.put("stackTrace", arr2);
                    Collections.addAll(arr2, trace.split("\n"));
                    jo.put("lastError", eo);
                }
            }
        }

        if (trigger) {
            if (wait) {
                if (failed) {
                    root.put("status", "failed");
                } else {
                    root.put("status", "success");
                }
            } else {
                root.put("status", "reloading");
            }
        }
        return root;
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
