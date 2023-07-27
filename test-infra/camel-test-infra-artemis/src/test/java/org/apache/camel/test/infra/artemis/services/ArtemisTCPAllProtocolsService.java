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
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.remoting.impl.netty.NettyConnectorFactory;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.camel.test.AvailablePortFinder;

import static org.junit.jupiter.api.Assertions.fail;

public class ArtemisTCPAllProtocolsService extends AbstractArtemisEmbeddedService {

    private String brokerURL;
    private int port;

    @Override
    protected Configuration configure(Configuration configuration, int port, int brokerId) {
        this.port = port;

        port = AvailablePortFinder.getNextAvailable();
        brokerURL = "tcp://0.0.0.0:" + port;

        configuration.setPersistenceEnabled(false);
        try {
            configuration.addAcceptorConfiguration("in-vm", "vm://" + brokerId);
            configuration.addAcceptorConfiguration("connector", brokerURL + "?protocols=CORE,AMQP,HORNETQ,OPENWIRE,MQTT");
            configuration.addConnectorConfiguration("connector",
                    new TransportConfiguration(NettyConnectorFactory.class.getName()));
            configuration.setJournalDirectory("target/data/journal");
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            fail("vm acceptor cannot be configured");
        }
        configuration.addAddressSetting("#",
                new AddressSettings()
                        .setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL)
                        .setDeadLetterAddress(SimpleString.toSimpleString("DLQ"))
                        .setExpiryAddress(SimpleString.toSimpleString("ExpiryQueue")));

        return configuration;
    }

    @Override
    public String serviceAddress() {
        return brokerURL;
    }

    @Override
    public int brokerPort() {
        return port;
    }
}
