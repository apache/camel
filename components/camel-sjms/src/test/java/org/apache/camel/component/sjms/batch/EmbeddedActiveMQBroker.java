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
package org.apache.camel.component.sjms.batch;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JUnit Test aspect that creates an embedded ActiveMQ broker at the beginning of each test and shuts it down after.
 */
public class EmbeddedActiveMQBroker extends ExternalResource {

    private final Logger log = LoggerFactory.getLogger(EmbeddedActiveMQBroker.class);
    private final String brokerId;
    private BrokerService brokerService;
    private final String tcpConnectorUri;

    public EmbeddedActiveMQBroker(String brokerId) {
        if ((brokerId == null) || (brokerId.isEmpty())) {
            throw new IllegalArgumentException("brokerId is empty");
        }
        this.brokerId = brokerId;
        tcpConnectorUri = "tcp://localhost:" + AvailablePortFinder.getNextAvailable();

        brokerService = new BrokerService();
        brokerService.setBrokerId(brokerId);
        brokerService.setPersistent(false);
        brokerService.setUseJmx(false);
        try {
            brokerService.setPersistenceAdapter(new MemoryPersistenceAdapter());
            brokerService.addConnector(tcpConnectorUri);
        } catch (Exception e) {
            throw new RuntimeException("Problem creating brokerService", e);
        }
    }

    @Override
    protected void before() throws Throwable {
        log.info("Starting embedded broker[{}] on {}", brokerId, tcpConnectorUri);
        brokerService.start();
    }

    @Override
    protected void after() {
        try {
            log.info("Stopping embedded broker[{}]", brokerId);
            brokerService.stop();
        } catch (Exception e) {
            throw new RuntimeException("Exception shutting down broker service", e);
        }
    }

    public String getTcpConnectorUri() {
        return tcpConnectorUri;
    }
}
