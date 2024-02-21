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

import org.apache.camel.test.infra.common.services.TestService;

public interface RabbitMQService extends TestService {

    /**
     * The connection properties for the service
     *
     * @return The connection properties (host, port, username, password)
     */
    ConnectionProperties connectionProperties();

    /**
     * Gets the connection URI
     *
     * @return the connection URI in the format amqp://host:port
     */
    default String getAmqpUrl() {
        ConnectionProperties properties = connectionProperties();
        return String.format("amqp://%s:%s", properties.hostname(), properties.port());
    }

    /**
     * Gets the HTTP port
     *
     * @return the HTTP port
     */
    int getHttpPort();

    /**
     * Perform any initialization necessary
     */
    void initialize();

    /**
     * Shuts down the service after the test has completed
     */
    void shutdown();
}
