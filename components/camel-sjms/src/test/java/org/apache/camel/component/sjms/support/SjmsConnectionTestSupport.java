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
package org.apache.camel.component.sjms.support;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.broker.BrokerService;
import org.apache.camel.util.ObjectHelper;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SjmsConnectionTestSupport {

    static {
        System.setProperty("org.apache.activemq.default.directory.prefix", "target/activemq/");
    }
    
    public static final String VM_BROKER_CONNECT_STRING = "vm://broker";
    public static final String TCP_BROKER_CONNECT_STRING = "tcp://localhost:61616";
    protected Logger logger = LoggerFactory.getLogger(getClass());
    private ActiveMQConnectionFactory vmTestConnectionFactory;
    private ActiveMQConnectionFactory testConnectionFactory;
    private BrokerService brokerService;
    private boolean persistenceEnabled;

    public abstract String getConnectionUri();

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
    }

    @Before
    public void setup() throws Exception {
        if (ObjectHelper.isEmpty(getConnectionUri())
                || getConnectionUri().startsWith("vm")) {
            vmTestConnectionFactory = new ActiveMQConnectionFactory(
                    VM_BROKER_CONNECT_STRING);
        } else {
            createBroker();
        }
    }

    @After
    public void teardown() throws Exception {

        if (vmTestConnectionFactory != null) {
            vmTestConnectionFactory = null;
        }
        if (testConnectionFactory != null) {
            testConnectionFactory = null;
        }
        if (brokerService != null) {
            destroyBroker();
        }
    }

    public ActiveMQConnectionFactory createTestConnectionFactory(String uri) {
        ActiveMQConnectionFactory cf = null;
        if (ObjectHelper.isEmpty(uri)) {
            cf = new ActiveMQConnectionFactory(VM_BROKER_CONNECT_STRING);
        } else {
            cf = new ActiveMQConnectionFactory(uri);
        }
        return cf;
    }

    protected void createBroker() throws Exception {
        String connectString = getConnectionUri();
        if (ObjectHelper.isEmpty(connectString)) {
            connectString = TCP_BROKER_CONNECT_STRING;
        }
        brokerService = new BrokerService();
        brokerService.setPersistent(isPersistenceEnabled());
        brokerService.addConnector(connectString);
        brokerService.start();
        brokerService.waitUntilStarted();
    }

    protected void destroyBroker() throws Exception {
        if (brokerService != null) {
            brokerService.stop();
            brokerService.waitUntilStopped();
        }
    }

    public void setTestConnectionFactory(
            ActiveMQConnectionFactory testConnectionFactory) {
        this.testConnectionFactory = testConnectionFactory;
    }

    public ActiveMQConnectionFactory getTestConnectionFactory() {
        return testConnectionFactory;
    }

    public void setPersistenceEnabled(boolean persistenceEnabled) {
        this.persistenceEnabled = persistenceEnabled;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }
}
