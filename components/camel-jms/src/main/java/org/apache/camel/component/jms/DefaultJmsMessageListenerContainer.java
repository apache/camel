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

import org.apache.camel.spi.ExecutorServiceManager;
import org.apache.camel.util.concurrent.CamelThreadFactory;
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
     * <p>The default implementation builds a {@link ThreadPoolTaskExecutor} with the following parameters:
     * <ul>
     * <li>corePoolSize = concurrentConsumers</li>
     * <li>maxPoolSize = maxConcurrentConsumers</li>
     * </ul>
     * It uses the specified bean name and Camel's {@link org.apache.camel.spi.ExecutorServiceManager}
     * to resolve the thread name.
     * @see ThreadPoolTaskExecutor#setBeanName(String)
     */
    @Override
    protected TaskExecutor createDefaultTaskExecutor() {
        ExecutorServiceManager esm = endpoint.getCamelContext().getExecutorServiceManager();
        String pattern = esm.getThreadNamePattern();
        String beanName = getBeanName();

        ThreadPoolTaskExecutor answer = new ThreadPoolTaskExecutor();
        answer.setBeanName(beanName);
        answer.setThreadFactory(new CamelThreadFactory(pattern, beanName, true));
        answer.setCorePoolSize(endpoint.getConcurrentConsumers());
        answer.setMaxPoolSize(endpoint.getMaxConcurrentConsumers());
        return answer;
    }

}
