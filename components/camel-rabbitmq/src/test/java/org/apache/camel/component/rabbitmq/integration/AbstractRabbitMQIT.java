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
package org.apache.camel.component.rabbitmq.integration;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import org.apache.camel.component.rabbitmq.test.infra.services.RabbitMQServiceFactory;
import org.apache.camel.test.infra.rabbitmq.services.ConnectionProperties;
import org.apache.camel.test.infra.rabbitmq.services.RabbitMQService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractRabbitMQIT extends CamelTestSupport {
    // Note: this is using the RabbitMQService from this module so that we can also run
    // tests using the embedded QPid broker.
    @RegisterExtension
    public static RabbitMQService service = RabbitMQServiceFactory.createService();

    protected Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Helper method for creating a RabbitMQ connection to the test instance of the RabbitMQ server.
     *
     * @return
     * @throws IOException
     * @throws TimeoutException
     */
    protected Connection connection() throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        ConnectionProperties properties = service.connectionProperties();

        factory.setHost(properties.hostname());

        factory.setPort(properties.port());

        factory.setUsername(properties.username());

        factory.setPassword(properties.password());
        factory.setVirtualHost("/");
        return factory.newConnection();
    }

}
