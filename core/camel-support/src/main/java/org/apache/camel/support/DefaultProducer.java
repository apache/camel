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
package org.apache.camel.support;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation of {@link Producer} for implementation inheritance.
 */
public abstract class DefaultProducer extends ServiceSupport implements Producer {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultProducer.class);

    private transient String producerToString;
    private final Endpoint endpoint;

    public DefaultProducer(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String toString() {
        if (producerToString == null) {
            producerToString = "Producer[" + URISupport.sanitizeUri(endpoint.getEndpointUri()) + "]";
        }
        return producerToString;
    }

    @Override
    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Exchange createExchange() {
        return endpoint.createExchange();
    }

    /**
     * This implementation will delegate to the endpoint {@link org.apache.camel.Endpoint#isSingleton()}
     */
    @Override
    public boolean isSingleton() {
        return endpoint.isSingleton();
    }

    @Override
    protected void doStart() throws Exception {
        // log at debug level for singletons, for prototype scoped log at trace level to not spam logs
        if (isSingleton()) {
            LOG.debug("Starting producer: {}", this);
        } else {
            LOG.trace("Starting producer: {}", this);
        }
    }

    @Override
    protected void doStop() throws Exception {
        // log at debug level for singletons, for prototype scoped log at trace level to not spam logs
        if (isSingleton()) {
            LOG.debug("Stopping producer: {}", this);
        } else {
            LOG.trace("Stopping producer: {}", this);
        }
    }
}
