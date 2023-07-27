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

import static org.junit.jupiter.api.Assertions.fail;

public class ArtemisMQTTService extends AbstractArtemisEmbeddedService {

    private String brokerURL;
    private int port;

    public ArtemisMQTTService(int port) {
        super(port);
    }

    public ArtemisMQTTService() {
        super();
    }

    @Override
    protected Configuration configure(Configuration configuration, int port, int brokerId) {
        this.port = port;
        brokerURL = "tcp://0.0.0.0:" + port;

        try {
            configuration.addAcceptorConfiguration("mqtt", brokerURL + "?protocols=MQTT");
        } catch (Exception e) {
            LOG.warn(e.getMessage(), e);
            fail("mqtt acceptor cannot be configured");
        }

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
