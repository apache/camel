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
import org.apache.camel.util.ObjectHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
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
    //    private BrokerService brokerService;
    private boolean persistenceEnabled;

    public abstract String getConnectionUri();

    @BeforeAll
    public static void setUpBeforeClass() {
    }

    @AfterAll
    public static void tearDownAfterClass() {
    }

    @BeforeEach
    public void setup() throws Exception {
        if (ObjectHelper.isEmpty(getConnectionUri())
                || getConnectionUri().startsWith("vm")) {
            vmTestConnectionFactory = new ActiveMQConnectionFactory(
                    VM_BROKER_CONNECT_STRING);
        } else {
            createBroker();
        }
    }

    @AfterEach
    public void teardown() throws Exception {

        if (vmTestConnectionFactory != null) {
            vmTestConnectionFactory = null;
        }
        if (testConnectionFactory != null) {
            testConnectionFactory = null;
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
