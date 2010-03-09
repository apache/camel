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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Helper for {@link java.util.concurrent.ExecutorService} to construct executors using a thread factory that
 * create thread names with Camel prefix.
 *
 * @version $Revision$
 * @deprecated replaced with {@link org.apache.camel.spi.ExecutorServiceStrategy}
 */
@Deprecated
public final class ExecutorServiceHelper {

    private static AtomicInteger threadCounter = new AtomicInteger();

    private ExecutorServiceHelper() {
    }

    private static synchronized int nextThreadCounter() {
        return threadCounter.getAndIncrement();
    }

    /**
     * Creates a new thread name with the given prefix
     *
     * @param pattern the pattern
     * @param name the name
     * @return the thread name, which is unique
     */
    public static String getThreadName(String pattern, String name) {
        String answer = pattern.replaceFirst("\\$\\{counter\\}", ""  + nextThreadCounter());
        answer = answer.replaceFirst("\\$\\{name\\}", name);
        if (answer.indexOf("$") > -1 || answer.indexOf("${") > -1 || answer.indexOf("}") > -1) {
            throw new IllegalArgumentException("Pattern is invalid: " + pattern);
        }

        return answer;
    }

    /**
     * Creates a new scheduled thread pool which can schedule threads.
     *
     * @param poolSize the core pool size
     * @param name     part of the thread name
     * @param daemon   whether the threads is daemon or not
     * @return the created pool
     */
    public static ScheduledExecutorService newScheduledThreadPool(final int poolSize, final String name, final boolean daemon) {
        return Executors.newScheduledThreadPool(poolSize, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, name);
                answer.setDaemon(daemon);
                return answer;
            }
        });
    }

    public static ExecutorService newFixedThreadPool(final int poolSize, final String name, final boolean daemon) {
        return Executors.newFixedThreadPool(poolSize, new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, name);
                answer.setDaemon(daemon);
                return answer;
            }
        });
    }

    public static ExecutorService newSingleThreadExecutor(final String name, final boolean daemon) {
        return Executors.newSingleThreadExecutor(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, name);
                answer.setDaemon(daemon);
                return answer;
            }
        });
    }

    /**
     * Creates a new cached thread pool which should be the most commonly used.
     *
     * @param name    the full thread name
     * @param daemon  whether the threads is daemon or not
     * @return the created pool
     */
    public static ExecutorService newCachedThreadPool(final String name, final boolean daemon) {
        return Executors.newCachedThreadPool(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, name);
                answer.setDaemon(daemon);
                return answer;
            }
        });
    }

    /**
     * Creates a new custom thread pool using 60 seconds as keep alive
     *
     * @param name          the full thread name
     * @param corePoolSize  the core size
     * @param maxPoolSize   the maximum pool size
     * @return the created pool
     */
    public static ExecutorService newThreadPool(final String name, int corePoolSize, int maxPoolSize) {
        return ExecutorServiceHelper.newThreadPool(name, corePoolSize, maxPoolSize, 60, TimeUnit.SECONDS, true);
    }

    /**
     * Creates a new custom thread pool
     *
     * @param name          the full thread name
     * @param corePoolSize  the core size
     * @param maxPoolSize   the maximum pool size
     * @param keepAliveTime keep alive
     * @param timeUnit      keep alive time unit
     * @param daemon        whether the threads is daemon or not
     * @return the created pool
     * @throws IllegalArgumentException if parameters is not valid
     */
    public static ExecutorService newThreadPool(final String name, int corePoolSize, int maxPoolSize,
                                                long keepAliveTime, TimeUnit timeUnit, final boolean daemon) {

        // validate max >= core
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("MaxPoolSize must be >= corePoolSize, was " + maxPoolSize + " >= " + corePoolSize);
        }

        ThreadPoolExecutor answer = new ThreadPoolExecutor(corePoolSize, maxPoolSize,
                                                           keepAliveTime, timeUnit, new LinkedBlockingQueue<Runnable>());
        answer.setThreadFactory(new ThreadFactory() {
            public Thread newThread(Runnable r) {
                Thread answer = new Thread(r, name);
                answer.setDaemon(daemon);
                return answer;
            }
        });
        return answer;
    }

}
