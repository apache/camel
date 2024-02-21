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
package org.apache.camel.component.jms;

import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.JmsException;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The default {@link DefaultMessageListenerContainer container} which listen for messages on the JMS destination.
 * <p/>
 * This implementation extends Springs {@link DefaultMessageListenerContainer} supporting automatic recovery and
 * throttling.
 */
public class DefaultJmsMessageListenerContainer extends DefaultMessageListenerContainer {

    private final JmsEndpoint endpoint;
    private final boolean allowQuickStop;
    private volatile TaskExecutor taskExecutor;

    public DefaultJmsMessageListenerContainer(JmsEndpoint endpoint) {
        this(endpoint, true);
    }

    public DefaultJmsMessageListenerContainer(JmsEndpoint endpoint, boolean allowQuickStop) {
        this.endpoint = endpoint;
        this.allowQuickStop = allowQuickStop;
    }

    /**
     * Whether this {@link DefaultMessageListenerContainer} allows the {@link #runningAllowed()} to quick stop in case
     * {@link JmsConfiguration#isAcceptMessagesWhileStopping()} is enabled, and {@link org.apache.camel.CamelContext} is
     * currently being stopped.
     */
    protected boolean isAllowQuickStop() {
        return allowQuickStop;
    }

    @Override
    protected boolean runningAllowed() {
        // we can stop quickly if CamelContext is being stopped, and we do not accept messages while stopping
        // this allows a more cleanly shutdown of the message listener
        boolean quickStop = false;
        if (isAllowQuickStop() && !endpoint.isAcceptMessagesWhileStopping()) {
            quickStop = endpoint.getCamelContext().getStatus().isStopping();
        }

        if (quickStop) {
            // log at debug level so its quicker to see we are stopping quicker from the logs
            logger.debug(
                    "runningAllowed() -> false due CamelContext is stopping and endpoint configured to not accept messages while stopping");
            return false;
        } else {
            // otherwise we only run if the endpoint is running
            boolean answer = endpoint.isRunning();
            // log at trace level as otherwise this can be noisy during normal operation
            logger.trace("runningAllowed() -> " + answer);
            return answer;
        }
    }

    /**
     * Create a default TaskExecutor. Called if no explicit TaskExecutor has been specified.
     * <p />
     * The type of {@link TaskExecutor} will depend on the value of
     * {@link JmsConfiguration#getDefaultTaskExecutorType()}. For more details, refer to the Javadoc of
     * {@link DefaultTaskExecutorType}.
     * <p />
     * In all cases, it uses the specified bean name and Camel's {@link org.apache.camel.spi.ExecutorServiceManager} to
     * resolve the thread name.
     *
     * @see JmsConfiguration#setDefaultTaskExecutorType(DefaultTaskExecutorType)
     * @see ThreadPoolTaskExecutor#setBeanName(String)
     */
    @Override
    protected TaskExecutor createDefaultTaskExecutor() {
        String pattern = endpoint.getCamelContext().getExecutorServiceManager().getThreadNamePattern();
        String beanName = getBeanName() == null ? endpoint.getThreadName() : getBeanName();

        TaskExecutor answer;

        if (endpoint.getDefaultTaskExecutorType() == DefaultTaskExecutorType.ThreadPool) {
            answer = createThreadPoolExecutor(beanName, pattern);
        } else {
            answer = createAsyncTaskExecutor(beanName, pattern);
        }

        taskExecutor = answer;
        return answer;
    }

    private static TaskExecutor createAsyncTaskExecutor(String beanName, String pattern) {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(beanName);
        executor.setThreadFactory(new CamelThreadFactory(pattern, beanName, true));
        return executor;
    }

    private TaskExecutor createThreadPoolExecutor(String beanName, String pattern) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setBeanName(beanName);
        executor.setThreadFactory(new CamelThreadFactory(pattern, beanName, true));
        executor.setCorePoolSize(endpoint.getConcurrentConsumers());
        // Direct hand-off mode. Do not queue up tasks: assign it to a thread immediately.
        // We set no upper-bound on the thread pool (no maxPoolSize) as it's already implicitly constrained by
        // maxConcurrentConsumers on the DMLC itself (i.e. DMLC will only grow up to a level of concurrency as
        // defined by maxConcurrentConsumers).
        executor.setQueueCapacity(0);
        executor.initialize();
        return executor;
    }

    @Override
    public void stop() throws JmsException {
        if (logger.isDebugEnabled()) {
            logger.debug("Stopping listenerContainer: " + this + " with cacheLevel: " + getCacheLevel()
                         + " and sharedConnectionEnabled: " + sharedConnectionEnabled());
        }
        super.stop();

        if (taskExecutor instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) taskExecutor;
            executor.destroy();
        }
    }

    @Override
    public void destroy() {
        if (logger.isDebugEnabled()) {
            logger.debug("Destroying listenerContainer: " + this + " with cacheLevel: " + getCacheLevel()
                         + " and sharedConnectionEnabled: " + sharedConnectionEnabled());
        }
        super.destroy();

        if (taskExecutor instanceof ThreadPoolTaskExecutor) {
            ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor) taskExecutor;
            executor.destroy();
        }
    }

    @Override
    protected void stopSharedConnection() {
        if (logger.isDebugEnabled()) {
            if (sharedConnectionEnabled()) {
                logger.debug("Stopping shared connection on listenerContainer: " + this);
            }
        }
        super.stopSharedConnection();
    }
}
