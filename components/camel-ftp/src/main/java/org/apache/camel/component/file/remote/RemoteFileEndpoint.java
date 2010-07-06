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
package org.apache.camel.component.file.remote;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileConfiguration;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.util.ObjectHelper;

/**
 * Remote file endpoint.
 */
public abstract class RemoteFileEndpoint<T> extends GenericFileEndpoint<T> {

    private int maximumReconnectAttempts = 3;
    private long reconnectDelay = 1000;
    private boolean disconnect;

    public RemoteFileEndpoint() {
        // no args constructor for spring bean endpoint configuration
    }

    public RemoteFileEndpoint(String uri, RemoteFileComponent<T> component, RemoteFileConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public RemoteFileConfiguration getConfiguration() {
        return (RemoteFileConfiguration) this.configuration;
    }

    @Override
    public Exchange createExchange(GenericFile<T> file) {
        Exchange answer = new DefaultExchange(this);
        if (file != null) {
            file.bindToExchange(answer);
        }
        return answer;
    }

    @Override
    public GenericFileProducer<T> createProducer() throws Exception {
        afterPropertiesSet();
        return buildProducer();
    }

    @Override
    public RemoteFileConsumer<T> createConsumer(Processor processor) throws Exception {
        afterPropertiesSet();
        RemoteFileConsumer<T> consumer = buildConsumer(processor);

        if (isDelete() && getMove() != null) {
            throw new IllegalArgumentException("You cannot both set delete=true and move options");
        }
        // if noop=true then idempotent should also be configured
        if (isNoop() && !isIdempotent()) {
            log.info("Endpoint is configured with noop=true so forcing endpoint to be idempotent as well");
            setIdempotent(true);
        }

        // if idempotent and no repository set then create a default one
        if (isIdempotent() && idempotentRepository == null) {
            log.info("Using default memory based idempotent repository with cache max size: " + DEFAULT_IDEMPOTENT_CACHE_SIZE);
            idempotentRepository = MemoryIdempotentRepository.memoryIdempotentRepository(DEFAULT_IDEMPOTENT_CACHE_SIZE);
        }

        // set max messages per poll
        consumer.setMaxMessagesPerPoll(getMaxMessagesPerPoll());

        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Validates this endpoint if its configured properly.
     *
     * @throws Exception is thrown if endpoint is invalid configured for its mandatory options
     */
    protected void afterPropertiesSet() throws Exception {
        RemoteFileConfiguration config = (RemoteFileConfiguration) getConfiguration();
        ObjectHelper.notEmpty(config.getHost(), "host");
        ObjectHelper.notEmpty(config.getProtocol(), "protocol");
    }

    /**
     * Remote File Endpoints, impl this method to create a custom consumer specific to their "protocol" etc.
     *
     * @param processor  the processor
     * @return the created consumer
     */
    protected abstract RemoteFileConsumer<T> buildConsumer(Processor processor);

    /**
     * Remote File Endpoints, impl this method to create a custom producer specific to their "protocol" etc.
     *
     * @return the created producer
     */
    protected abstract GenericFileProducer<T> buildProducer();

    /**
     * Creates the operations to be used by the consumer or producer.
     *
     * @return a new created operations
     * @throws Exception is thrown if error creating operations.
     */
    protected abstract RemoteFileOperations<T> createRemoteFileOperations() throws Exception;

    /**
     * Returns human readable server information for logging purpose
     */
    public String remoteServerInformation() {
        return ((RemoteFileConfiguration) configuration).remoteServerInformation();
    }
    
    @Override
    public char getFileSeparator() {       
        return '/';
    }
    
    @Override
    public boolean isAbsolute(String name) {        
        return name.startsWith("/");
    }

    public int getMaximumReconnectAttempts() {
        return maximumReconnectAttempts;
    }

    public void setMaximumReconnectAttempts(int maximumReconnectAttempts) {
        this.maximumReconnectAttempts = maximumReconnectAttempts;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    public void setReconnectDelay(long reconnectDelay) {
        this.reconnectDelay = reconnectDelay;
    }

    public boolean isDisconnect() {
        return disconnect;
    }

    public void setDisconnect(boolean disconnect) {
        this.disconnect = disconnect;
    }
}
