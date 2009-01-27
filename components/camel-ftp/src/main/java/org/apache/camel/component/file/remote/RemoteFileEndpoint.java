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

import org.apache.camel.Processor;
import org.apache.camel.component.file.GenericFile;
import org.apache.camel.component.file.GenericFileEndpoint;
import org.apache.camel.component.file.GenericFileExchange;
import org.apache.camel.component.file.GenericFileProducer;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.apache.camel.util.ObjectHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Remote file endpoint.
 */
public abstract class RemoteFileEndpoint<T> extends GenericFileEndpoint<T> {

    protected final transient Log log = LogFactory.getLog(getClass());

    public RemoteFileEndpoint() {
        // no args constructor for spring bean endpoint configuration
    }

    public RemoteFileEndpoint(String uri, RemoteFileComponent<T> component, RemoteFileOperations<T> operations,
                              RemoteFileConfiguration configuration) {
        super(uri, component);
        this.operations = operations;
        this.configuration = configuration;
    }

    @Override
    public GenericFileExchange createExchange() {
        return new RemoteFileExchange(this, getExchangePattern());
    }

    @Override
    public GenericFileExchange createExchange(GenericFile<T> file) {
        return new RemoteFileExchange(this, getExchangePattern(), (RemoteFile<T>) file);
    }

    @Override
    public GenericFileProducer<T> createProducer() throws Exception {
        return new RemoteFileProducer<T>(this, (RemoteFileOperations<T>) this.operations);
    }

    @Override
    public RemoteFileConsumer createConsumer(Processor processor) throws Exception {
        String protocol = ((RemoteFileConfiguration) getConfiguration()).getProtocol();
        ObjectHelper.notEmpty(protocol, "protocol");

        RemoteFileConsumer consumer = buildConsumer(processor, (RemoteFileOperations<T>) operations);

        if (isDelete() && (getMoveNamePrefix() != null || getMoveNamePostfix() != null || getExpression() != null)) {
            throw new IllegalArgumentException("You cannot set delete=true and a moveNamePrefix, moveNamePostfix or expression option");
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

        configureConsumer(consumer);
        return consumer;
    }

    /**
     * Remote File Endpoints, impl this method to create a custom consumer specific to their "protocol" etc.
     *
     * @param processor  the processor
     * @param operations the operations
     * @return the created consumer
     */
    protected abstract RemoteFileConsumer buildConsumer(Processor processor, RemoteFileOperations<T> operations);

    /**
     * Returns human readable server information for logging purpose
     */
    public String remoteServerInformation() {
        return ((RemoteFileConfiguration) configuration).remoteServerInformation();
    }

}
