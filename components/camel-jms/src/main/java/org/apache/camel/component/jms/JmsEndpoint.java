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

import javax.jms.Message;
import javax.jms.TemporaryQueue;
import javax.jms.TemporaryTopic;

import org.apache.camel.ExchangePattern;
import org.apache.camel.PollingConsumer;
import org.apache.camel.Processor;
import org.apache.camel.component.jms.requestor.Requestor;
import org.apache.camel.impl.DefaultEndpoint;
import org.springframework.jms.core.JmsOperations;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.listener.AbstractMessageListenerContainer;

/**
 * A <a href="http://activemq.apache.org/jms.html">JMS Endpoint</a>
 *
 * @version $Revision:520964 $
 */
public class JmsEndpoint extends DefaultEndpoint<JmsExchange> {
    private final boolean pubSubDomain;
    private JmsBinding binding;
    private String destination;
    private String selector;
    private JmsConfiguration configuration;
    private Requestor requestor;
    private long requestTimeout;

    public JmsEndpoint(String uri, JmsComponent component, String destination, boolean pubSubDomain, JmsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
        this.destination = destination;
        this.pubSubDomain = pubSubDomain;
        this.requestTimeout = configuration.getRequestTimeout();
    }

    public JmsEndpoint(String endpointUri, JmsBinding binding, JmsConfiguration configuration, String destination, boolean pubSubDomain) {
        super(endpointUri);
        this.binding = binding;
        this.configuration = configuration;
        this.destination = destination;
        this.pubSubDomain = pubSubDomain;
        this.requestTimeout = configuration.getRequestTimeout();
    }

    public JmsEndpoint(String endpointUri, String destination, boolean pubSubDomain) {
        this(endpointUri, new JmsBinding(), new JmsConfiguration(), destination, pubSubDomain);
    }

    /**
     * Creates a pub-sub endpoint with the given destination
     */
    public JmsEndpoint(String endpointUri, String destination) {
        this(endpointUri, destination, true);
    }

    public JmsProducer createProducer() throws Exception {
        return new JmsProducer(this);
    }

    /**
     * Creates a producer using the given template for InOnly message exchanges
     */
    public JmsProducer createProducer(JmsOperations template) throws Exception {
        JmsProducer answer = createProducer();
        if (template instanceof JmsTemplate) {
            JmsTemplate jmsTemplate = (JmsTemplate) template;
            jmsTemplate.setPubSubDomain(pubSubDomain);
            jmsTemplate.setDefaultDestinationName(destination);
        }
        answer.setInOnlyTemplate(template);
        return answer;
    }

    public JmsConsumer createConsumer(Processor processor) throws Exception {
        AbstractMessageListenerContainer listenerContainer = configuration.createMessageListenerContainer(this);
        return createConsumer(processor, listenerContainer);
    }

    /**
     * Creates a consumer using the given processor and listener container
     *
     * @param processor         the processor to use to process the messages
     * @param listenerContainer the listener container
     * @return a newly created consumer
     * @throws Exception if the consumer cannot be created
     */
    public JmsConsumer createConsumer(Processor processor, AbstractMessageListenerContainer listenerContainer) throws Exception {
        listenerContainer.setDestinationName(destination);
        listenerContainer.setPubSubDomain(pubSubDomain);
        return new JmsConsumer(this, processor, listenerContainer);
    }

    @Override
    public PollingConsumer<JmsExchange> createPollingConsumer() throws Exception {
        JmsOperations template = createInOnlyTemplate();
        return new JmsPollingConsumer(this, template);
    }

    @Override
    public JmsExchange createExchange(ExchangePattern pattern) {
        return new JmsExchange(getCamelContext(), pattern, getBinding());
    }

    public JmsExchange createExchange(Message message) {
        return new JmsExchange(getCamelContext(), getExchangePattern(), getBinding(), message);
    }

    /**
     * Factory method for creating a new template for InOnly message exchanges
     */
    public JmsOperations createInOnlyTemplate() {
        return configuration.createInOnlyTemplate(this, pubSubDomain, destination);
    }

    /**
     * Factory method for creating a new template for InOut message exchanges
     */
    public JmsOperations createInOutTemplate() {
        return configuration.createInOutTemplate(this, pubSubDomain, destination, getRequestTimeout());
    }

    // Properties
    // -------------------------------------------------------------------------
    public JmsBinding getBinding() {
        if (binding == null) {
            binding = new JmsBinding(this);
        }
        return binding;
    }

    /**
     * Sets the binding used to convert from a Camel message to and from a JMS
     * message
     *
     * @param binding the binding to use
     */
    public void setBinding(JmsBinding binding) {
        this.binding = binding;
    }

    public String getDestination() {
        return destination;
    }

    public JmsConfiguration getConfiguration() {
        return configuration;
    }

    public String getSelector() {
        return selector;
    }

    /**
     * Sets the JMS selector to use
     */
    public void setSelector(String selector) {
        this.selector = selector;
    }

    public boolean isSingleton() {
        return false;
    }

    public synchronized Requestor getRequestor() throws Exception {
        if (requestor == null) {
            requestor = new Requestor(getConfiguration(), getExecutorService());
            requestor.start();
        }
        return requestor;
    }

    public void setRequestor(Requestor requestor) {
        this.requestor = requestor;
    }

    public long getRequestTimeout() {
        return requestTimeout;
    }

    /**
     * Sets the timeout in milliseconds which requests should timeout after
     *
     * @param requestTimeout
     */
    public void setRequestTimeout(long requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public boolean isPubSubDomain() {
        return pubSubDomain;
    }

    /**
     * Lazily loads the temporary queue type if one has not been explicitly configured
     * via calling the {@link JmsProviderMetadata#setTemporaryQueueType(Class)}
     * on the {@link #getConfiguration()} instance
     */
    public Class<? extends TemporaryQueue> getTemporaryQueueType() {
        JmsProviderMetadata metadata = getProviderMetadata();
        JmsOperations template = getMetadataJmsOperations();
        return metadata.getTemporaryQueueType(template);
    }

    /**
     * Lazily loads the temporary topic type if one has not been explicitly configured
     * via calling the {@link JmsProviderMetadata#setTemporaryTopicType(Class)}
     * on the {@link #getConfiguration()} instance
     */
    public Class<? extends TemporaryTopic> getTemporaryTopicType() {
        JmsOperations template = getMetadataJmsOperations();
        JmsProviderMetadata metadata = getProviderMetadata();
        return metadata.getTemporaryTopicType(template);
    }

    /**
     * Returns the provider metadata
     */
    protected JmsProviderMetadata getProviderMetadata() {
        JmsConfiguration conf = getConfiguration();
        JmsProviderMetadata metadata = conf.getProviderMetadata();
        return metadata;
    }

    /**
     * Returns the {@link JmsOperations} used for metadata operations such as creating temporary destinations
     */
    protected JmsOperations getMetadataJmsOperations() {
        JmsOperations template = getConfiguration().getMetadataJmsOperations(this);
        if (template == null) {
            throw new IllegalArgumentException("No Metadata JmsTemplate supplied!");
        }
        return template;
    }

    public void checkValidTemplate(JmsTemplate template) {
        if (template.getDestinationResolver() == null) {
            if (this instanceof DestinationEndpoint) {
                final DestinationEndpoint destinationEndpoint = (DestinationEndpoint) this;
                template.setDestinationResolver(JmsConfiguration.createDestinationResolver(destinationEndpoint));
            }
        }
    }
}
