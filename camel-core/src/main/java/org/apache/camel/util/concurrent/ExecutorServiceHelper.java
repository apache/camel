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

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.camel.model.ExecutorServiceAwareDefinition;
import org.apache.camel.spi.ExecutorServiceStrategy;
import org.apache.camel.spi.RouteContext;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper for {@link java.util.concurrent.ExecutorService} to construct executors using a thread factory that
 * create thread names with Camel prefix.
 * <p/>
 * This helper should <b>NOT</b> be used by end users of Camel, as you should use
 * {@link org.apache.camel.spi.ExecutorServiceStrategy} which you obtain from {@link org.apache.camel.CamelContext}
 * to create thread pools.
 * <p/>
 * This helper should only be used internally in Camel.
 *
 * @version 
 */
public final class ExecutorServiceHelper {

    public static final String DEFAULT_PATTERN = "Camel Thread ${counter} - ${name}";
    private static final Logger LOG = LoggerFactory.getLogger(ExecutorServiceHelper.class);
    private static AtomicLong threadCounter = new AtomicLong();

    private ExecutorServiceHelper() {
    }

    private static long nextThreadCounter() {
        return threadCounter.getAndIncrement();
    }

    /**
     * Creates a new thread name with the given prefix
     *
     * @param pattern the pattern
     * @param name    the name
     * @return the thread name, which is unique
     */
    public static String getThreadName(String pattern, String name) {
        if (pattern == null) {
            pattern = DEFAULT_PATTERN;
        }

        // the name could potential have a $ sign we want to keep
        if (name.indexOf("$") > -1) {
            name = name.replaceAll("\\$", "CAMEL_REPLACE_ME");
        }

        // we support ${longName} and ${name} as name placeholders
        String longName = name;
        String shortName = name.contains("?") ? ObjectHelper.before(name, "?") : name;

        String answer = pattern.replaceFirst("\\$\\{counter\\}", "" + nextThreadCounter());
        answer = answer.replaceFirst("\\$\\{longName\\}", longName);
        answer = answer.replaceFirst("\\$\\{name\\}", shortName);
        if (answer.indexOf("$") > -1 || answer.indexOf("${") > -1 || answer.indexOf("}") > -1) {
            throw new IllegalArgumentException("Pattern is invalid: " + pattern);
        }

        if (answer.indexOf("CAMEL_REPLACE_ME") > -1) {
            answer = answer.replaceAll("CAMEL_REPLACE_ME", "\\$");
        }

        return answer;
    }

    /**
     * Creates a new scheduled thread pool which can schedule threads.
     *
     * @param poolSize the core pool size
     * @param pattern  pattern of the thread name
     * @param name     ${name} in the pattern name
     * @param daemon   whether the threads is daemon or not
     * @return the created pool
     */
    public static ScheduledExecutorService newScheduledThreadPool(final int poolSize, final String pattern, final String name, final boolean daemon) {
        return Executors.newScheduledThreadPool(poolSize, new CamelThreadFactory(pattern, name, daemon));
    }

    /**
     * Creates a new fixed thread pool.
     * <p/>
     * Beware that the task queue is unbounded
     *
     * @param poolSize the fixed pool size
     * @param pattern  pattern of the thread name
     * @param name     ${name} in the pattern name
     * @param daemon   whether the threads is daemon or not
     * @return the created pool
     */
    public static ExecutorService newFixedThreadPool(final int poolSize, final String pattern, final String name, final boolean daemon) {
        return Executors.newFixedThreadPool(poolSize, new CamelThreadFactory(pattern, name, daemon));
    }

    /**
     * Creates a new single thread pool (usually for background tasks)
     *
     * @param pattern pattern of the thread name
     * @param name    ${name} in the pattern name
     * @param daemon  whether the threads is daemon or not
     * @return the created pool
     */
    public static ExecutorService newSingleThreadExecutor(final String pattern, final String name, final boolean daemon) {
        return Executors.newSingleThreadExecutor(new CamelThreadFactory(pattern, name, daemon));
    }

    /**
     * Creates a new cached thread pool.
     * <p/>
     * <b>Important:</b> Using cached thread pool is discouraged as they have no upper bound and can overload the JVM.
     *
     * @param pattern pattern of the thread name
     * @param name    ${name} in the pattern name
     * @param daemon  whether the threads is daemon or not
     * @return the created pool
     */
    public static ExecutorService newCachedThreadPool(final String pattern, final String name, final boolean daemon) {
        return Executors.newCachedThreadPool(new CamelThreadFactory(pattern, name, daemon));
    }

    /**
     * Creates a new synchronous executor service which always executes the task in the call thread
     * (its just a thread pool facade)
     *
     * @return the created pool
     * @see org.apache.camel.util.concurrent.SynchronousExecutorService
     */
    public static ExecutorService newSynchronousThreadPool() {
        return new SynchronousExecutorService();
    }

    /**
     * Creates a new custom thread pool using 60 seconds as keep alive and with an unbounded queue.
     *
     * @param pattern      pattern of the thread name
     * @param name         ${name} in the pattern name
     * @param corePoolSize the core pool size
     * @param maxPoolSize  the maximum pool size
     * @return the created pool
     */
    public static ExecutorService newThreadPool(final String pattern, final String name, int corePoolSize, int maxPoolSize) {
        return ExecutorServiceHelper.newThreadPool(pattern, name, corePoolSize, maxPoolSize, 60,
                TimeUnit.SECONDS, -1, new ThreadPoolExecutor.CallerRunsPolicy(), true);
    }

    /**
     * Creates a new custom thread pool using 60 seconds as keep alive and with bounded queue.
     *
     * @param pattern      pattern of the thread name
     * @param name         ${name} in the pattern name
     * @param corePoolSize the core pool size
     * @param maxPoolSize  the maximum pool size
     * @param maxQueueSize the maximum number of tasks in the queue, use <tt>Integer.MAX_VALUE</tt> or <tt>-1</tt> to indicate unbounded
     * @return the created pool
     */
    public static ExecutorService newThreadPool(final String pattern, final String name, int corePoolSize, int maxPoolSize, int maxQueueSize) {
        return ExecutorServiceHelper.newThreadPool(pattern, name, corePoolSize, maxPoolSize, 60,
                TimeUnit.SECONDS, maxQueueSize, new ThreadPoolExecutor.CallerRunsPolicy(), true);
    }

    /**
     * Creates a new custom thread pool
     *
     * @param pattern                  pattern of the thread name
     * @param name                     ${name} in the pattern name
     * @param corePoolSize             the core pool size
     * @param maxPoolSize              the maximum pool size
     * @param keepAliveTime            keep alive time
     * @param timeUnit                 keep alive time unit
     * @param maxQueueSize             the maximum number of tasks in the queue, use <tt>Integer.MAX_VALUE</tt> or <tt>-1</tt> to indicate unbounded
     * @param rejectedExecutionHandler the handler for tasks which cannot be executed by the thread pool.
     *                                 If <tt>null</tt> is provided then {@link java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy CallerRunsPolicy} is used.
     * @param daemon                   whether the threads is daemon or not
     * @return the created pool
     * @throws IllegalArgumentException if parameters is not valid
     */
    public static ExecutorService newThreadPool(final String pattern, final String name, int corePoolSize, int maxPoolSize,
                                                long keepAliveTime, TimeUnit timeUnit, int maxQueueSize,
                                                RejectedExecutionHandler rejectedExecutionHandler, final boolean daemon) {

        // validate max >= core
        if (maxPoolSize < corePoolSize) {
            throw new IllegalArgumentException("MaxPoolSize must be >= corePoolSize, was " + maxPoolSize + " >= " + corePoolSize);
        }

        BlockingQueue<Runnable> queue;
        if (corePoolSize == 0 && maxQueueSize <= 0) {
            // use a synchronous queue
            queue = new SynchronousQueue<Runnable>();
            // and force 1 as pool size to be able to create the thread pool by the JDK
            corePoolSize = 1;
            maxPoolSize = 1;
        } else if (maxQueueSize <= 0) {
            // unbounded task queue
            queue = new LinkedBlockingQueue<Runnable>();
        } else {
            // bounded task queue
            queue = new LinkedBlockingQueue<Runnable>(maxQueueSize);
        }
        ThreadPoolExecutor answer = new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, timeUnit, queue);
        answer.setThreadFactory(new CamelThreadFactory(pattern, name, daemon));
        if (rejectedExecutionHandler == null) {
            rejectedExecutionHandler = new ThreadPoolExecutor.CallerRunsPolicy();
        }
        answer.setRejectedExecutionHandler(rejectedExecutionHandler);
        return answer;
    }

    /**
     * Will lookup and get the configured {@link java.util.concurrent.ExecutorService} from the given definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     *   <li>from the definition if any explicit configured executor service.</li>
     *   <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     *   <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     *   <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper method to ensure they support
     * configured executor services in the same coherent way.
     *
     * @param routeContext   the rout context
     * @param name           name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                       is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition     the node definition which may leverage executor service.
     * @return the configured executor service, or <tt>null</tt> if none was configured.
     * @throws IllegalArgumentException is thrown if lookup of executor service in {@link org.apache.camel.spi.Registry} was not found
     */
    public static ExecutorService getConfiguredExecutorService(RouteContext routeContext, String name,
                                                               ExecutorServiceAwareDefinition definition) throws IllegalArgumentException {
        ExecutorServiceStrategy strategy = routeContext.getCamelContext().getExecutorServiceStrategy();
        ObjectHelper.notNull(strategy, "ExecutorServiceStrategy", routeContext.getCamelContext());

        // prefer to use explicit configured executor on the definition
        if (definition.getExecutorService() != null) {
            return definition.getExecutorService();
        } else if (definition.getExecutorServiceRef() != null) {
            ExecutorService answer = strategy.lookup(definition, name, definition.getExecutorServiceRef());
            if (answer == null) {
                throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() + " not found in registry.");
            }
            return answer;
        }

        return null;
    }

    /**
     * Will lookup and get the configured {@link java.util.concurrent.ScheduledExecutorService} from the given definition.
     * <p/>
     * This method will lookup for configured thread pool in the following order
     * <ul>
     *   <li>from the definition if any explicit configured executor service.</li>
     *   <li>from the {@link org.apache.camel.spi.Registry} if found</li>
     *   <li>from the known list of {@link org.apache.camel.spi.ThreadPoolProfile ThreadPoolProfile(s)}.</li>
     *   <li>if none found, then <tt>null</tt> is returned.</li>
     * </ul>
     * The various {@link ExecutorServiceAwareDefinition} should use this helper method to ensure they support
     * configured executor services in the same coherent way.
     *
     * @param routeContext   the rout context
     * @param name           name which is appended to the thread name, when the {@link java.util.concurrent.ExecutorService}
     *                       is created based on a {@link org.apache.camel.spi.ThreadPoolProfile}.
     * @param definition     the node definition which may leverage executor service.
     * @return the configured executor service, or <tt>null</tt> if none was configured.
     * @throws IllegalArgumentException is thrown if lookup of executor service in {@link org.apache.camel.spi.Registry} was not found
     *                                  or the found instance is not a ScheduledExecutorService type.
     */
    public static ScheduledExecutorService getConfiguredScheduledExecutorService(RouteContext routeContext, String name,
                                                               ExecutorServiceAwareDefinition definition) throws IllegalArgumentException {
        ExecutorServiceStrategy strategy = routeContext.getCamelContext().getExecutorServiceStrategy();
        ObjectHelper.notNull(strategy, "ExecutorServiceStrategy", routeContext.getCamelContext());

        // prefer to use explicit configured executor on the definition
        if (definition.getExecutorService() != null) {
            ExecutorService executorService = definition.getExecutorService();
            if (executorService instanceof ScheduledExecutorService) {
                return (ScheduledExecutorService) executorService;
            }
            throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() + " is not an ScheduledExecutorService instance");
        } else if (definition.getExecutorServiceRef() != null) {
            ScheduledExecutorService answer = strategy.lookupScheduled(definition, name, definition.getExecutorServiceRef());
            if (answer == null) {
                throw new IllegalArgumentException("ExecutorServiceRef " + definition.getExecutorServiceRef() + " not found in registry.");
            }
            return answer;
        }

        return null;
    }

    /**
     * Timeout the completion service.
     * <p/>
     * This can be used to mark the completion service as timed out, allowing you to poll any already completed tasks.
     * This applies when using the {@link SubmitOrderedCompletionService}.
     *
     * @param completionService the completion service.
     */
    public static void timeoutTask(CompletionService completionService) {
        if (completionService instanceof SubmitOrderedCompletionService) {
            ((SubmitOrderedCompletionService) completionService).timeoutTask();
        }
    }

    /**
     * Thread factory which creates threads supporting a naming pattern.
     */
    private static final class CamelThreadFactory implements ThreadFactory {

        private final String pattern;
        private final String name;
        private final boolean daemon;

        private CamelThreadFactory(String pattern, String name, boolean daemon) {
            this.pattern = pattern;
            this.name = name;
            this.daemon = daemon;
        }

        public Thread newThread(Runnable runnable) {
            String threadName = getThreadName(pattern, name);
            Thread answer = new Thread(runnable, threadName);
            answer.setDaemon(daemon);

            LOG.trace("Created thread[{}]: {}", name, answer);
            return answer;
        }

        public String toString() {
            return "CamelThreadFactory[" + name + "]";
        }
    }

}
