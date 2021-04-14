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

package org.apache.camel.component.rabbitmq.test.infra.services;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.component.rabbitmq.integration.AbstractRabbitMQIT;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.apache.camel.test.infra.rabbitmq.services.RabbitMQService;
import org.apache.qpid.server.SystemLauncher;
import org.apache.qpid.server.model.ConfiguredObject;
import org.apache.qpid.server.model.SystemConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class QpidEmbeddedService implements RabbitMQService {
    protected static final String INITIAL_CONFIGURATION = "qpid-test-initial-config.json";
    protected static SystemLauncher systemLauncher = new SystemLauncher();

    private static final Logger LOG = LoggerFactory.getLogger(QpidEmbeddedService.class);

    /**
     * Helper method for creating a Qpid Broker-J system configuration for the initiate of the local AMQP server.
     */
    protected static Map<String, Object> createQpidSystemConfig() {
        Map<String, Object> attributes = new HashMap<>();
        URL initialConfig = AbstractRabbitMQIT.class.getClassLoader().getResource(INITIAL_CONFIGURATION);
        attributes.put(ConfiguredObject.TYPE, "Memory");
        attributes.put(SystemConfig.INITIAL_CONFIGURATION_LOCATION, initialConfig.toExternalForm());
        attributes.put(SystemConfig.STARTUP_LOGGED_TO_SYSTEM_OUT, false);

        return attributes;
    }

    @Override
    public ConnectionProperties connectionProperties() {
        return new ConnectionProperties() {
            @Override
            public String username() {
                return "cameltest";
            }

            @Override
            public String password() {
                return "cameltest";
            }

            @Override
            public String hostname() {
                return "localhost";
            }

            @Override
            public int port() {
                return 5672;
            }
        };
    }

    @Override
    public int getHttpPort() {
        throw new UnsupportedOperationException("Qpid embedded broker does not have a HTTP admin service");
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start Qpid embedded container");
        try {
            systemLauncher.startup(createQpidSystemConfig());
            LOG.info("Qpid embedded service running on {}", getAmqpUrl());
        } catch (Exception e) {
            LOG.error("Initialization failed!", e);
            throw new IllegalStateException(e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        systemLauncher.shutdown();
    }
}
