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
package org.apache.camel.component.sjms.support;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A support class that builds up and tears down an ActiveMQ instance to be used for unit testing.
 */
public abstract class JmsTestSupport extends JmsCommonTestSupport {
    @RegisterExtension
    public static ArtemisService service = ArtemisServiceFactory.createSingletonVMService();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @Override
    protected String getBrokerUri() {
        return service.serviceAddress();
    }

    /**
     * Set up the Broker
     */
    @BeforeEach
    protected final void setupBrokerUri() {
        brokerUri = getBrokerUri();
    }

    @Override
    protected void setupFactoryExternal(ActiveMQConnectionFactory factory) {
        setupFactoryExternal(factory, service);
    }

}
