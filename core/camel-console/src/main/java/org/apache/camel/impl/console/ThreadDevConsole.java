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
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.Map;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonArray;
import org.apache.camel.util.json.JsonObject;

@DevConsole("thread")
@Configurer(bootstrap = true)
public class ThreadDevConsole extends AbstractDevConsole {

    public ThreadDevConsole() {
        super("jvm", "thread", "Thread", "Displays Threads information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        boolean st = "true".equals(options.getOrDefault("stackTrace", "false"));
        ThreadMXBean tb = ManagementFactory.getThreadMXBean();
        if (tb != null) {
            sb.append(String.format("Threads: %s\n", tb.getThreadCount()));
            sb.append(String.format("Daemon Threads: %s\n", tb.getDaemonThreadCount()));
            sb.append(String.format("Total Started Threads: %s\n", tb.getTotalStartedThreadCount()));
            sb.append(String.format("Peak Threads: %s\n", tb.getPeakThreadCount()));

            long[] ids = tb.getAllThreadIds();
            Arrays.sort(ids);
            for (long id : ids) {
                ThreadInfo ti = st ? tb.getThreadInfo(id, Integer.MAX_VALUE) : tb.getThreadInfo(id);
                if (ti != null) {
                    String lock = ti.getLockName() != null ? "locked: " + ti.getLockName() : "";
                    sb.append(String.format("\n    Thread %s: %s (%s) %s", id, ti.getThreadName(), ti.getThreadState().name(),
                            lock));
                    if (st) {
                        for (StackTraceElement e : ti.getStackTrace()) {
                            sb.append(String.format("\n        %s", e));
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected JsonObject doCallJson(Map<String, Object> options) {
        JsonObject root = new JsonObject();

        boolean st = "true".equals(options.getOrDefault("stackTrace", "false"));
        ThreadMXBean tb = ManagementFactory.getThreadMXBean();
        if (tb != null) {
            root.put("threadCount", tb.getThreadCount());
            root.put("daemonThreadCount", tb.getDaemonThreadCount());
            root.put("totalStartedThreadCount", tb.getTotalStartedThreadCount());
            root.put("peakThreadCount", tb.getPeakThreadCount());

            JsonArray arr = new JsonArray();
            root.put("threads", arr);

            long[] ids = tb.getAllThreadIds();
            Arrays.sort(ids);
            for (long id : ids) {
                ThreadInfo ti = st ? tb.getThreadInfo(id, Integer.MAX_VALUE) : tb.getThreadInfo(id);
                if (ti != null) {
                    JsonObject jo = new JsonObject();
                    jo.put("id", ti.getThreadId());
                    jo.put("name", ti.getThreadName());
                    jo.put("state", ti.getThreadState().name());
                    jo.put("blockedCount", ti.getBlockedCount());
                    jo.put("blockedTime", ti.getBlockedTime());
                    jo.put("waitedCount", ti.getWaitedCount());
                    jo.put("waitedTime", ti.getWaitedTime());
                    if (ti.getLockName() != null) {
                        jo.put("lockName", ti.getLockName());
                    }
                    if (st) {
                        JsonArray arr2 = new JsonArray();
                        jo.put("stackTrace", arr2);
                        for (StackTraceElement e : ti.getStackTrace()) {
                            arr2.add(e.toString());
                        }
                    }
                    arr.add(jo);
                }
            }
        }

        return root;
    }

}
