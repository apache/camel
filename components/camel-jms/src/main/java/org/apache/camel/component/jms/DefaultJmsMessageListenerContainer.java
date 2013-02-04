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
package org.apache.camel.component.jms;

import org.apache.camel.util.concurrent.CamelThreadFactory;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * The default {@link DefaultMessageListenerContainer container} which listen for messages
 * on the JMS destination.
 * <p/>
 * This implementation extends Springs {@link DefaultMessageListenerContainer} supporting
 * automatic recovery and throttling.
 *
 * @version 
 */
public class DefaultJmsMessageListenerContainer extends DefaultMessageListenerContainer {

    private final JmsEndpoint endpoint;

    public DefaultJmsMessageListenerContainer(JmsEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    protected boolean runningAllowed() {
        // do not run if we have been stopped
        return endpoint.isRunning();
    }

    /**
     * Create a default TaskExecutor. Called if no explicit TaskExecutor has been specified.
     * <p />
     * The type of {@link TaskExecutor} will depend on the value of
     * {@link JmsConfiguration#getDefaultTaskExecutorType()}. For more details, refer to the Javadoc of
     * {@link DefaultTaskExecutorType}.
     * <p />
     * In all cases, it uses the specified bean name and Camel's {@link org.apache.camel.spi.ExecutorServiceManager}
     * to resolve the thread name.
     * @see JmsConfiguration#setDefaultTaskExecutorType(DefaultTaskExecutorType)
     * @see ThreadPoolTaskExecutor#setBeanName(String)
     */
    @Override
    protected TaskExecutor createDefaultTaskExecutor() {
        String pattern = endpoint.getCamelContext().getExecutorServiceManager().getThreadNamePattern();
        String beanName = getBeanName() == null ? endpoint.getThreadName() : getBeanName();

        if (endpoint.getDefaultTaskExecutorType() == DefaultTaskExecutorType.ThreadPool) {
            ThreadPoolTaskExecutor answer = new ThreadPoolTaskExecutor();
            answer.setBeanName(beanName);
            answer.setThreadFactory(new CamelThreadFactory(pattern, beanName, true));
            answer.setCorePoolSize(endpoint.getConcurrentConsumers());
            // Direct hand-off mode. Do not queue up tasks: assign it to a thread immediately.
            // We set no upper-bound on the thread pool (no maxPoolSize) as it's already implicitly constrained by
            // maxConcurrentConsumers on the DMLC itself (i.e. DMLC will only grow up to a level of concurrency as
            // defined by maxConcurrentConsumers).
            answer.setQueueCapacity(0);
            answer.initialize();
            return answer;
        } else {
            SimpleAsyncTaskExecutor answer = new SimpleAsyncTaskExecutor(beanName);
            answer.setThreadFactory(new CamelThreadFactory(pattern, beanName, true));
            return answer;
        }
    }
    
}
