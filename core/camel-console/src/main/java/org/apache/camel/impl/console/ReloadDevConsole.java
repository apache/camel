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
import java.util.Set;
import java.util.concurrent.ExecutorService;

import org.apache.camel.spi.ReloadStrategy;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole("reload")
public class ReloadDevConsole extends AbstractDevConsole {

    /**
     * Option to trigger reloading
     */
    public static final String RELOAD = "reload";

    // reload on demand should run async to avoid blocking
    private volatile ExecutorService reloadThread;

    public ReloadDevConsole() {
        super("camel", "reload", "Reload", "Console for reloading running Camel");
    }

    protected String doCallText(Map<String, Object> options) {
        String trigger = (String) options.get(RELOAD);
        StringBuilder sb = new StringBuilder();

        Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
        for (ReloadStrategy r : rs) {
            if ("true".equals(trigger)) {
                getOrCreateReloadTask().submit(() -> r.onReload("ReloadDevConsole"));
            } else {
                sb.append(String.format("\nReloadStrategy: %s", r.getClass().getName()));
                sb.append(String.format("\n    Reloaded: %s", r.getReloadCounter()));
                sb.append(String.format("\n    Failed: %s", r.getFailedCounter()));
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    protected JsonObject doCallJson(Map<String, Object> options) {
        String trigger = (String) options.get(RELOAD);
        JsonObject root = new JsonObject();

        JsonArray arr = new JsonArray();
        Set<ReloadStrategy> rs = getCamelContext().hasServices(ReloadStrategy.class);
        for (ReloadStrategy r : rs) {
            if ("true".equals(trigger)) {
                getOrCreateReloadTask().submit(() -> r.onReload("ReloadDevConsole"));
            } else {
                if (root.isEmpty()) {
                    root.put("reloadStrategies", arr);
                }
                JsonObject jo = new JsonObject();
                arr.add(jo);
                jo.put("className", r.getClass().getName());
                jo.put("reloaded", r.getReloadCounter());
                jo.put("failed", r.getFailedCounter());
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
