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
package org.apache.camel.test.infra.artemis.services;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

import static org.junit.jupiter.api.Assertions.fail;

public class ArtemisTCPService extends AbstractArtemisEmbeddedService {

    private String brokerUrl;
    private int tcpPort;

    public ArtemisTCPService() {
    }

    @Override
    protected Configuration getConfiguration(Configuration configuration, int port) {
        this.tcpPort = port;
        brokerUrl = "tcp://0.0.0.0:" + port
                    + "?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;protocols=CORE,AMQP,STOMP,HORNETQ,MQTT,OPENWIRE";
        configuration.setPersistenceEnabled(false);
        try {
            configuration.addAcceptorConfiguration("artemis", brokerUrl);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            fail("Artemis/TCP acceptor cannot be configured");
        }
        configuration.addAddressSetting("#",
                new AddressSettings()
                        .setDeadLetterAddress(SimpleString.toSimpleString("DLQ"))
                        .setExpiryAddress(SimpleString.toSimpleString("ExpiryQueue")));

        return configuration;
    }

    @Override
    public String serviceAddress() {
        return brokerUrl;
    }

    @Override
    public int brokerPort() {
        return tcpPort;
    }
}
