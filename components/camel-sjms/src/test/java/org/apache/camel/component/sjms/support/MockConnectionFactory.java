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

import java.net.URI;
import java.net.URISyntaxException;
import javax.jms.Connection;
import javax.jms.JMSException;
import org.apache.activemq.ActiveMQConnection;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.management.JMSStatsImpl;
import org.apache.activemq.transport.Transport;

public class MockConnectionFactory extends ActiveMQConnectionFactory {
    private int returnBadSessionNTimes;

    public MockConnectionFactory(String brokerURL) {
        super(createURI(brokerURL));
    }
    public Connection createConnection() throws JMSException {
        return this.createActiveMQConnection();
    }
    private static URI createURI(String brokerURL) {
        try {
            return new URI(brokerURL);
        } catch (URISyntaxException var2) {
            throw (IllegalArgumentException)(new IllegalArgumentException("Invalid broker URI: " + brokerURL)).initCause(var2);
        }
    }

    protected ActiveMQConnection createActiveMQConnection(Transport transport, JMSStatsImpl stats) throws Exception {
        MockConnection connection = new MockConnection(transport, this.getClientIdGenerator(), this.getConnectionIdGenerator(), stats, returnBadSessionNTimes);
        return connection;
    }

    public void returnBadSessionNTimes(int returnBadSessionNTimes) {
        this.returnBadSessionNTimes = returnBadSessionNTimes;
    }

}
