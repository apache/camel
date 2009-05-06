/**
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
package org.apache.camel.util.concurrent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Helper for {@link java.util.concurrent.ExecutorService} to construct executors using a thread factory that
 * create thread names with Camel prefix.
 *
 * @version $Revision$
 */
public final class ExecutorServiceHelper {

    private static int threadCounter;

    private ExecutorServiceHelper() {
    }

    /**
     * Creates a new thread name with the given prefix
     */
    protected static String getThreadName(String name) {
        return "Camel " + name + " thread:" + nextThreadCounter();
    }

    protected static synchronized int nextThreadCounter() {
        return ++threadCounter;
    }

    public static ScheduledExecutorService newScheduledThreadPool(final int poolSize, final String name, final boolean daemon) {
        return Executors.newScheduledThreadPool(poolSize, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, getThreadName(name));
                answer.setDaemon(daemon);
                return answer;
            }
        });
    }

    public static ExecutorService newFixedThreadPool(final int poolSize, final String name, final boolean daemon) {
        return Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, getThreadName(name));
                answer.setDaemon(daemon);
                return answer;
            }
        });
    }

    public static ExecutorService newSingleThreadExecutor(final String name, final boolean daemon) {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, getThreadName(name));
                answer.setDaemon(daemon);
                return answer;
            }
        });
    }

}
