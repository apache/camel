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

package org.apache.camel.test.infra.rabbitmq.services;

import org.apache.camel.test.infra.rabbitmq.common.RabbitMQProperties;

public class RabbitMQRemoteService implements RabbitMQService {

    @Override
    public ConnectionProperties connectionProperties() {
        return new ConnectionProperties() {
            @Override
            public String username() {
                return System.getProperty(RabbitMQProperties.RABBITMQ_USER_NAME);
            }

            @Override
            public String password() {
                return System.getProperty(RabbitMQProperties.RABBITMQ_USER_PASSWORD);
            }

            @Override
            public String hostname() {
                return System.getProperty(RabbitMQProperties.RABBITMQ_CONNECTION_HOSTNAME);
            }

            @Override
            public int port() {
                String amqPort = System.getProperty(RabbitMQProperties.RABBITMQ_CONNECTION_AMQP, "5672");

                return Integer.parseInt(amqPort);
            }
        };
    }

    @Override
    public int getHttpPort() {
        String httpPort = System.getProperty(RabbitMQProperties.RABBITMQ_CONNECTION_HTTP, "15672");

        return Integer.parseInt(httpPort);
    }

    @Override
    public void registerProperties() {
        // NO-OP
    }

    @Override
    public void initialize() {
        registerProperties();
    }

    @Override
    public void shutdown() {

    }
}
