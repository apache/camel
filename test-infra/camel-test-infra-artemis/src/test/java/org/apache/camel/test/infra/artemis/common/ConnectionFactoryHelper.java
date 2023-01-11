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
package org.apache.camel.test.infra.artemis.common;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.test.infra.artemis.services.AbstractArtemisEmbeddedService;
import org.apache.camel.test.infra.artemis.services.ArtemisService;
import org.apache.camel.test.infra.artemis.services.ArtemisServiceFactory;
import org.apache.camel.test.infra.messaging.services.ConnectionFactoryAware;

public final class ConnectionFactoryHelper {
    private ConnectionFactoryHelper() {
    }

    public static ConnectionFactory createConnectionFactory(ArtemisService service) {
        return createConnectionFactory(service, null);
    }

    public static ConnectionFactory createConnectionFactory(ArtemisService service, Integer maximumRedeliveries) {
        if (service instanceof ConnectionFactoryAware) {
            AbstractArtemisEmbeddedService embeddedService = (AbstractArtemisEmbeddedService) service;

            return createConnectionFactory(embeddedService.serviceAddress(), maximumRedeliveries);
        }

        if (service instanceof ArtemisServiceFactory.SingletonArtemisService) {
            return createConnectionFactory(((ArtemisServiceFactory.SingletonArtemisService) service).getService(),
                    maximumRedeliveries);
        }

        throw new UnsupportedOperationException(
                String.format("The test service %s does not implement ConnectionFactoryAware", service.getClass()));
    }

    public static ConnectionFactory createConnectionFactory(String url, Integer maximumRedeliveries) {
        return createConnectionFactory(new ActiveMQConnectionFactory(url), maximumRedeliveries);
    }

    public static ConnectionFactory createConnectionFactory(
            ActiveMQConnectionFactory connectionFactory, Integer maximumRedeliveries) {
        return connectionFactory;
    }

    public static ConnectionFactory createPersistentConnectionFactory(String url) {
        return createPersistentConnectionFactory(new ActiveMQConnectionFactory(url));
    }

    public static ConnectionFactory createPersistentConnectionFactory(ActiveMQConnectionFactory connectionFactory) {
        return connectionFactory;
    }
}
