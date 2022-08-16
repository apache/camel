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

package org.apache.camel.test.infra.activemq.services;

import org.apache.activemq.broker.BrokerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A regular embedded broker that uses the standard transport protocols (i.e.: AMQP, OpenWire, etc)
 */
public class ActiveMQEmbeddedService extends AbstractActiveMQEmbeddedService {
    private static final Logger LOG = LoggerFactory.getLogger(ActiveMQEmbeddedService.class);

    public ActiveMQEmbeddedService() {
        this(ActiveMQEmbeddedServiceBuilder.defaultBroker().brokerService());
    }

    public ActiveMQEmbeddedService(BrokerService brokerService) {
        this(brokerService, false);
    }

    public ActiveMQEmbeddedService(BrokerService brokerService, boolean recycle) {
        super(brokerService, recycle);
    }

    public String getVmURL() {
        return getVmURL(true, false);
    }

    @Override
    public String getVmURL(boolean create) {
        return null;
    }

    @Override
    public String serviceAddress() {
        return getBrokerUri(0);
    }
}
