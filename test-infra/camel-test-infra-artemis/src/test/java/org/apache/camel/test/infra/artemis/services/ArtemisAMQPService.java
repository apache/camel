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

import org.apache.activemq.artemis.api.core.RoutingType;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.CoreAddressConfiguration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;

import static org.junit.jupiter.api.Assertions.fail;

public class ArtemisAMQPService extends AbstractArtemisEmbeddedService {

    private String brokerURL;
    private int amqpPort;

    public ArtemisAMQPService() {
    }

    @Override
    protected Configuration configure(Configuration artemisConfiguration, int port, int brokerId) {
        amqpPort = port;
        brokerURL = "tcp://0.0.0.0:" + amqpPort
                    + "?tcpSendBufferSize=1048576;tcpReceiveBufferSize=1048576;protocols=AMQP;useEpoll=true;amqpCredits=1000;amqpMinCredits=300";

        AddressSettings addressSettings = new AddressSettings();
        addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);

        // Disable auto create address to make sure that topic name is correct without prefix
        try {
            artemisConfiguration.addAcceptorConfiguration("amqp", brokerURL);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            fail("AMQP acceptor cannot be configured");
        }
        artemisConfiguration.setPersistenceEnabled(false);
        artemisConfiguration.addAddressesSetting("#", addressSettings);
        artemisConfiguration.setSecurityEnabled(false);
        artemisConfiguration.setMaxDiskUsage(98);

        // Set explicit topic name
        CoreAddressConfiguration pingTopicConfig = new CoreAddressConfiguration();
        pingTopicConfig.setName("topic.ping");
        pingTopicConfig.addRoutingType(RoutingType.MULTICAST);

        artemisConfiguration.addAddressConfiguration(pingTopicConfig);

        return artemisConfiguration;
    }

    @Override
    public String serviceAddress() {
        return brokerURL;
    }

    @Override
    public int brokerPort() {
        return amqpPort;
    }
}
