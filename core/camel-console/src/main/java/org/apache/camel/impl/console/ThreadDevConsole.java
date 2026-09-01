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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.spi.Configurer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.DevConsole;
import org.apache.camel.support.console.AbstractDevConsole;
import org.apache.camel.util.json.JsonRecordSupport;

@DevConsole(name = "thread", description = "Displays JVM Threads information")
@Configurer(extended = true)
public class ThreadDevConsole extends AbstractDevConsole {

    @Metadata(label = "query", description = "Whether to include thread stack traces",
              defaultValue = "false", javaType = "java.lang.Boolean")
    public static final String STACK_TRACE = "stackTrace";

    public record ThreadEntry(
            @Metadata(description = "The thread ID") long id,
            @Metadata(description = "The thread name") String name,
            @Metadata(description = "The thread state") String state,
            @Metadata(description = "Number of times the thread has been blocked") long blockedCount,
            @Metadata(description = "Total time in milliseconds the thread has been blocked") long blockedTime,
            @Metadata(description = "Number of times the thread has waited") long waitedCount,
            @Metadata(description = "Total time in milliseconds the thread has waited") long waitedTime,
            @Metadata(description = "The name of the lock the thread is waiting on (only present when applicable)") String lockName,
            @Metadata(description = "The thread stack trace, one entry per line (only present when requested via the stackTrace option)") List<String> stackTrace) {
    }

    public record Response(
            @Metadata(description = "Number of threads") Integer threadCount,
            @Metadata(description = "Number of daemon threads") Integer daemonThreadCount,
            @Metadata(description = "Total number of threads started since JVM start") Long totalStartedThreadCount,
            @Metadata(description = "Peak number of threads") Integer peakThreadCount,
            @Metadata(description = "The threads") List<ThreadEntry> threads) {
    }

    public ThreadDevConsole() {
        super("jvm", "thread", "Thread", "Displays JVM Threads information");
    }

    @Override
    protected String doCallText(Map<String, Object> options) {
        StringBuilder sb = new StringBuilder();

        boolean st = optionBoolean(options, STACK_TRACE, false);
        ThreadMXBean tb = ManagementFactory.getThreadMXBean();
        if (tb != null) {
            sb.append(String.format("Threads: %s%n", tb.getThreadCount()));
            sb.append(String.format("Daemon Threads: %s%n", tb.getDaemonThreadCount()));
            sb.append(String.format("Total Started Threads: %s%n", tb.getTotalStartedThreadCount()));
            sb.append(String.format("Peak Threads: %s%n", tb.getPeakThreadCount()));

            long[] ids = tb.getAllThreadIds();
            Arrays.sort(ids);
            for (long id : ids) {
                ThreadInfo ti = st ? tb.getThreadInfo(id, Integer.MAX_VALUE) : tb.getThreadInfo(id);
                if (ti != null) {
                    String lock = ti.getLockName() != null ? "locked: " + ti.getLockName() : "";
                    sb.append(String.format("%n    Thread %s: %s (%s) %s", id, ti.getThreadName(), ti.getThreadState().name(),
                            lock));
                    if (st) {
                        for (StackTraceElement e : ti.getStackTrace()) {
                            sb.append(String.format("%n        %s", e));
                        }
                    }
                }
            }
        }

        return sb.toString();
    }

    @Override
    protected Map<String, Object> doCallJson(Map<String, Object> options) {
        boolean st = optionBoolean(options, STACK_TRACE, false);
        ThreadMXBean tb = ManagementFactory.getThreadMXBean();

        Response response;
        if (tb != null) {
            List<ThreadEntry> threads = new ArrayList<>();
            long[] ids = tb.getAllThreadIds();
            Arrays.sort(ids);
            for (long id : ids) {
                ThreadInfo ti = st ? tb.getThreadInfo(id, Integer.MAX_VALUE) : tb.getThreadInfo(id);
                if (ti != null) {
                    threads.add(toThreadEntry(ti, st));
                }
            }
            response = new Response(
                    tb.getThreadCount(), tb.getDaemonThreadCount(), tb.getTotalStartedThreadCount(),
                    tb.getPeakThreadCount(), threads);
        } else {
            response = new Response(null, null, null, null, null);
        }

        return JsonRecordSupport.toJsonObject(response);
    }

    private static ThreadEntry toThreadEntry(ThreadInfo ti, boolean st) {
        List<String> stackTrace = null;
        if (st) {
            stackTrace = new ArrayList<>();
            for (StackTraceElement e : ti.getStackTrace()) {
                stackTrace.add(e.toString());
            }
        }
        return new ThreadEntry(
                ti.getThreadId(), ti.getThreadName(), ti.getThreadState().name(), ti.getBlockedCount(), ti.getBlockedTime(),
                ti.getWaitedCount(), ti.getWaitedTime(), ti.getLockName(), stackTrace);
    }

}
