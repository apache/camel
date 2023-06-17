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
package org.apache.camel.component.digitalocean.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.digitalocean.DigitalOceanConfiguration;
import org.apache.camel.component.digitalocean.DigitalOceanEndpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanHeaders;
import org.apache.camel.component.digitalocean.constants.DigitalOceanOperations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The DigitalOcean producer.
 */
public abstract class DigitalOceanProducer extends DefaultProducer {
    protected static final Logger LOG = LoggerFactory.getLogger(DigitalOceanProducer.class);
    protected DigitalOceanConfiguration configuration;
    private DigitalOceanEndpoint endpoint;

    protected DigitalOceanProducer(DigitalOceanEndpoint endpoint, DigitalOceanConfiguration configuration) {
        super(endpoint);
        this.endpoint = endpoint;
        this.configuration = configuration;
    }

    protected DigitalOceanOperations determineOperation(Exchange exchange) {
        DigitalOceanOperations operation
                = exchange.getIn().getHeader(DigitalOceanHeaders.OPERATION, DigitalOceanOperations.class);
        return ObjectHelper.isNotEmpty(operation) ? operation : configuration.getOperation();
    }

    @Override
    public DigitalOceanEndpoint getEndpoint() {
        return endpoint;
    }
}
