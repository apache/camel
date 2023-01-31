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
package org.apache.camel.component.sjms;

import java.util.Map;
import java.util.concurrent.ExecutorService;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;

import org.apache.camel.CamelException;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.component.sjms.jms.DefaultDestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.DefaultJmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.DestinationCreationStrategy;
import org.apache.camel.component.sjms.jms.JmsKeyFormatStrategy;
import org.apache.camel.component.sjms.jms.MessageCreatedStrategy;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;

@Component("sjms")
public class SjmsComponent extends HeaderFilterStrategyComponent {

    private ExecutorService asyncStartStopExecutorService;

    @Metadata(label = "common", autowired = true,
              description = "The connection factory to be use. A connection factory must be configured either on the component or endpoint.")
    private ConnectionFactory connectionFactory;
    @UriParam(description = "Sets the JMS client ID to use. Note that this value, if specified, must be unique and can only be used by a single JMS connection instance."
                            + " It is typically only required for durable topic subscriptions."
                            + " If using Apache ActiveMQ you may prefer to use Virtual Topics instead.")
    private String clientId;
    @Metadata(label = "advanced",
              description = "Pluggable strategy for encoding and decoding JMS keys so they can be compliant with the JMS specification."
                            + " Camel provides one implementation out of the box: default. The default strategy will safely marshal dots and hyphens (. and -)."
                            + " Can be used for JMS brokers which do not care whether JMS header keys contain illegal characters. You can provide your own implementation"
                            + " of the org.apache.camel.component.jms.JmsKeyFormatStrategy and refer to it using the # notation.")
    private JmsKeyFormatStrategy jmsKeyFormatStrategy = new DefaultJmsKeyFormatStrategy();
    @Metadata(label = "advanced", description = "To use a custom DestinationCreationStrategy.")
    private DestinationCreationStrategy destinationCreationStrategy = new DefaultDestinationCreationStrategy();
    @Metadata(label = "advanced",
              description = "To use the given MessageCreatedStrategy which are invoked when Camel creates new instances"
                            + " of jakarta.jms.Message objects when Camel is sending a JMS message.")
    private MessageCreatedStrategy messageCreatedStrategy;
    @Metadata(defaultValue = "5000", label = "advanced", javaType = "java.time.Duration",
              description = "Specifies the interval between recovery attempts, i.e. when a connection is being refreshed, in milliseconds."
                            + " The default is 5000 ms, that is, 5 seconds.")
    private long recoveryInterval = 5000;
    @Metadata(defaultValue = "1000", label = "advanced", javaType = "java.time.Duration",
              description = "Configures how often Camel should check for timed out Exchanges when doing request/reply over JMS."
                            + " By default Camel checks once per second. But if you must react faster when a timeout occurs,"
                            + " then you can lower this interval, to check more frequently. The timeout is determined by the option requestTimeout.")
    private long requestTimeoutCheckerInterval = 1000L;
    @Metadata(label = "advanced", defaultValue = "1",
              description = "Specifies the maximum number of concurrent consumers for continue routing when timeout occurred when using request/reply over JMS.")
    private int replyToOnTimeoutMaxConcurrentConsumers = 1;

    @Metadata(label = "advanced",
              description = "Specifies the JMS Exception Listener that is to be notified of any underlying JMS exceptions.")
    private ExceptionListener exceptionListener;

    public SjmsComponent() {
    }

    protected SjmsComponent(Class<? extends Endpoint> endpointClass) {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        validateMepAndReplyTo(parameters);

        SjmsEndpoint endpoint = createSjmsEndpoint(uri, remaining);
        endpoint.setConnectionFactory(connectionFactory);
        endpoint.setDestinationCreationStrategy(destinationCreationStrategy);
        endpoint.setRecoveryInterval(recoveryInterval);
        endpoint.setMessageCreatedStrategy(messageCreatedStrategy);
        endpoint.setClientId(clientId);
        endpoint.setExceptionListener(exceptionListener);
        if (getHeaderFilterStrategy() != null) {
            endpoint.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

    protected SjmsEndpoint createSjmsEndpoint(String uri, String remaining) {
        return new SjmsEndpoint(uri, this, remaining);
    }

    /**
     * Helper method used to verify that when there is a replyTo value we are using the InOut MEP. If namedReplyTo is
     * defined and the MEP is InOnly the endpoint won't be expecting a reply so throw an error to alert the user.
     *
     * @param  parameters {@link Endpoint} parameters
     * @throws Exception  throws a {@link CamelException} when MEP equals InOnly and replyTo is defined.
     */
    private static void validateMepAndReplyTo(Map<String, Object> parameters) throws Exception {
        boolean replyToSet = parameters.containsKey("replyTo");
        boolean mepSet = parameters.containsKey("exchangePattern");
        if (replyToSet && mepSet) {
            if (!parameters.get("exchangePattern").equals(ExchangePattern.InOut.toString())) {
                String replyTo = (String) parameters.get("replyTo");
                ExchangePattern mep = ExchangePattern.valueOf((String) parameters.get("exchangePattern"));
                throw new CamelException(
                        "Setting parameter replyTo=" + replyTo
                                         + " requires a MEP of type InOut. Parameter exchangePattern is set to " + mep);
            }
        }
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
            asyncStartStopExecutorService
                    = getCamelContext().getExecutorServiceManager().newCachedThreadPool(this, "AsyncStartStopListener");
        }
        return asyncStartStopExecutorService;
    }

    public void setConnectionFactory(ConnectionFactory connectionFactory) {
        this.connectionFactory = connectionFactory;
    }

    public ConnectionFactory getConnectionFactory() {
        return connectionFactory;
    }

    public void setExceptionListener(ExceptionListener exceptionListener) {
        this.exceptionListener = exceptionListener;
    }

    public ExceptionListener getExceptionListener() {
        return exceptionListener;
    }

    public void setJmsKeyFormatStrategy(JmsKeyFormatStrategy jmsKeyFormatStrategy) {
        this.jmsKeyFormatStrategy = jmsKeyFormatStrategy;
    }

    public JmsKeyFormatStrategy getJmsKeyFormatStrategy() {
        return jmsKeyFormatStrategy;
    }

    public DestinationCreationStrategy getDestinationCreationStrategy() {
        return destinationCreationStrategy;
    }

    public void setDestinationCreationStrategy(DestinationCreationStrategy destinationCreationStrategy) {
        this.destinationCreationStrategy = destinationCreationStrategy;
    }

    public MessageCreatedStrategy getMessageCreatedStrategy() {
        return messageCreatedStrategy;
    }

    public void setMessageCreatedStrategy(MessageCreatedStrategy messageCreatedStrategy) {
        this.messageCreatedStrategy = messageCreatedStrategy;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public long getRecoveryInterval() {
        return recoveryInterval;
    }

    public void setRecoveryInterval(long recoveryInterval) {
        this.recoveryInterval = recoveryInterval;
    }

    public long getRequestTimeoutCheckerInterval() {
        return requestTimeoutCheckerInterval;
    }

    public void setRequestTimeoutCheckerInterval(long requestTimeoutCheckerInterval) {
        this.requestTimeoutCheckerInterval = requestTimeoutCheckerInterval;
    }

    public int getReplyToOnTimeoutMaxConcurrentConsumers() {
        return replyToOnTimeoutMaxConcurrentConsumers;
    }

    public void setReplyToOnTimeoutMaxConcurrentConsumers(int replyToOnTimeoutMaxConcurrentConsumers) {
        this.replyToOnTimeoutMaxConcurrentConsumers = replyToOnTimeoutMaxConcurrentConsumers;
    }
}
