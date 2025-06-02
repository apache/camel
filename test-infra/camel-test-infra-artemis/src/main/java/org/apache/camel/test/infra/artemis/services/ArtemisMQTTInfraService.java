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

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.settings.impl.AddressFullMessagePolicy;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.camel.test.infra.artemis.common.ArtemisRunException;

public class ArtemisMQTTInfraService extends AbstractArtemisEmbeddedService {

    private String brokerURL;
    private int port;

    public ArtemisMQTTInfraService(int port) {
        super(port);
    }

    public ArtemisMQTTInfraService() {
        super();
    }

    @Override
    protected Configuration configure(Configuration configuration, int port, int brokerId) {
        this.port = port;
        brokerURL = "tcp://0.0.0.0:" + port;

        AddressSettings addressSettings = new AddressSettings();
        addressSettings.setAddressFullMessagePolicy(AddressFullMessagePolicy.FAIL);

        try {
            configuration.addAcceptorConfiguration("mqtt", brokerURL + "?protocols=MQTT");

            configuration.addAddressSetting("#", addressSettings);
            configuration.setMaxDiskUsage(98);
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            throw new ArtemisRunException("mqtt acceptor cannot be configured", e);
        }

        return configuration;
    }

    @Override
    public String serviceAddress() {
        return brokerURL;
    }

    @Override
    public String remoteURI() {
        return brokerURL;
    }

    @Override
    public int brokerPort() {
        return port;
    }
}
