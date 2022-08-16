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
 * An embedded broker that relies on the VM transport for communication.
 *
 * @see <a href="https://activemq.apache.org/vm-transport-reference">VM Transport Reference</a>
 */
public class ActiveMQVMService extends AbstractActiveMQEmbeddedService {
    private static final Logger LOG = LoggerFactory.getLogger(ActiveMQVMService.class);

    public ActiveMQVMService() {
        this(ActiveMQEmbeddedServiceBuilder.defaultBroker().brokerService());
    }

    public ActiveMQVMService(BrokerService brokerService) {
        this(brokerService, true);
    }

    public ActiveMQVMService(BrokerService brokerService, boolean recycle) {
        super(brokerService, recycle);
    }

    @Override
    public String getVmURL() {
        return getVmURL(true);
    }

    @Override
    public String getVmURL(boolean create) {
        return getVmURL(false, create);
    }

    @Override
    public String serviceAddress() {
        return getVmURL(true);
    }

    @Override
    public String userName() {
        return null;
    }

    @Override
    public String password() {
        return null;
    }
}
