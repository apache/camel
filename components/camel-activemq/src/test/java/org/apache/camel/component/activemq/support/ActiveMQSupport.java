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
package org.apache.camel.component.activemq.support;

import java.nio.file.Path;

import org.apache.activemq.broker.BrokerService;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.test.junit5.CamelTestSupport;

public interface ActiveMQSupport {

    default String getBrokerUri(BrokerService broker) {
        try {
            return broker.getTransportConnectors().get(0).getPublishableConnectString();
        } catch (Exception e) {
            throw new RuntimeCamelException(e);
        }
    }

    default String vmUri() {
        return "vm://" + getClass().getSimpleName();
    }

    default String vmUri(String query) {
        return "vm://" + getClass().getSimpleName() + (query.startsWith("?") ? "" : "-") + query;
    }

    default Path testDirectory() {
        return CamelTestSupport.testDirectory(getClass(), false);
    }

    default BrokerService createBroker(boolean deleteAllMessages, boolean tcpTransport) throws Exception {
        return createBroker(null, deleteAllMessages, tcpTransport);
    }

    default BrokerService createBroker(String name, boolean deleteAllMessages, boolean tcpTransport) throws Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setDeleteAllMessagesOnStartup(deleteAllMessages);
        brokerService.setBrokerName(getClass().getSimpleName() + (name != null ? "-" + name : ""));
        brokerService.setAdvisorySupport(false);
        brokerService.setUseJmx(false);
        brokerService.setDataDirectory(testDirectory().toString());
        if (tcpTransport) {
            brokerService.addConnector("tcp://0.0.0.0:0");
        }
        return brokerService;
    }
}
