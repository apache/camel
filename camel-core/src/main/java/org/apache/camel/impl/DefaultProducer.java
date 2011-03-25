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
package org.apache.camel.impl;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Producer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A default implementation of {@link Producer} for implementation inheritance.
 *
 * @version 
 */
public abstract class DefaultProducer extends ServiceSupport implements Producer {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    private final Endpoint endpoint;

    public DefaultProducer(Endpoint endpoint) {
        this.endpoint = endpoint;
    }

    @Override
    public String toString() {
        return "Producer[" + endpoint.getEndpointUri() + "]";
    }

    public Endpoint getEndpoint() {
        return endpoint;
    }

    public Exchange createExchange() {
        return endpoint.createExchange();
    }

    public Exchange createExchange(ExchangePattern pattern) {
        return endpoint.createExchange(pattern);
    }

    public Exchange createExchange(Exchange exchange) {
        return endpoint.createExchange(exchange);
    }

    public boolean isSingleton() {
        return true;
    }

    protected void doStart() throws Exception {
        // log at debug level for singletons, for prototype scoped log at trace level to not spam logs
        if (isSingleton()) {
            if (log.isDebugEnabled()) {
                log.debug("Starting producer: " + this);
            }
        } else {
            log.trace("Starting producer: {}", this);
        }
    }

    protected void doStop() throws Exception {
        // log at debug level for singletons, for prototype scoped log at trace level to not spam logs
        if (isSingleton()) {
            if (log.isDebugEnabled()) {
                log.debug("Stopping producer: " + this);
            }
        } else {
            log.trace("Stopping producer: {}", this);
        }
    }
}
