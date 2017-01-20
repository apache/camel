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
package org.apache.camel.component.sjms.batch;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import javax.jms.ConnectionFactory;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.HeaderFilterStrategyComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

public class SjmsBatchComponent extends HeaderFilterStrategyComponent {

    private ExecutorService asyncStartStopExecutorService;

    @Metadata(label = "advanced")
    private ConnectionFactory connectionFactory;
    @Metadata(label = "advanced")
    private boolean asyncStartListener;
    @Metadata(label = "advanced", defaultValue = "5000")
    private int recoveryInterval = 5000;

    public SjmsBatchComponent() {
        super(SjmsBatchEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ConnectionFactory cf = resolveAndRemoveReferenceParameter(parameters, "connectionFactory", ConnectionFactory.class);
        if (cf != null) {
            setConnectionFactory(cf);
        }
        ObjectHelper.notNull(connectionFactory, "connectionFactory");

        SjmsBatchEndpoint answer = new SjmsBatchEndpoint(uri, this, remaining);
        answer.setAsyncStartListener(isAsyncStartListener());
        answer.setRecoveryInterval(getRecoveryInterval());
        setProperties(answer, parameters);

        return answer;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    /**
     * A ConnectionFactory is required to enable the SjmsBatchComponent.
     */
    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public boolean isAsyncStartListener() {
        return asyncStartListener;
    }

    /**
     * Whether to startup the consumer message listener asynchronously, when starting a route.
     * For example if a JmsConsumer cannot get a connection to a remote JMS broker, then it may block while retrying
     * and/or failover. This will cause Camel to block while starting routes. By setting this option to true,
     * you will let routes startup, while the JmsConsumer connects to the JMS broker using a dedicated thread
     * in asynchronous mode. If this option is used, then beware that if the connection could not be established,
     * then an exception is logged at WARN level, and the consumer will not be able to receive messages;
     * You can then restart the route to retry.
     */
    public void setAsyncStartListener(boolean asyncStartListener) {
        this.asyncStartListener = asyncStartListener;
    }

    public int getRecoveryInterval() {
        return recoveryInterval;
    }

    /**
     * Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in milliseconds.
     * The default is 5000 ms, that is, 5 seconds.
     */
    public void setRecoveryInterval(int recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    @Override
    protected void doShutdown() throws Exception {
        if (asyncStartStopExecutorService != null) {
            getCamelContext().getExecutorServiceManager().shutdownNow(asyncStartStopExecutorService);
            asyncStartStopExecutorService = null;
        }
        super.doShutdown();
    }

    protected synchronized ExecutorService getAsyncStartStopExecutorService() {
        if (asyncStartStopExecutorService == null) {
            // use a cached thread pool for async start tasks as they can run for a while, and we need a dedicated thread
            // for each task, and the thread pool will shrink when no more tasks running
            asyncStartStopExecutorService = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "AsyncStartStopListener");
        }
        return asyncStartStopExecutorService;
    }

}
