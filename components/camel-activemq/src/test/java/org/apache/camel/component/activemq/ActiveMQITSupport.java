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

package org.apache.camel.component.activemq;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.test.infra.artemis.services.ArtemisContainer;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.api.ConfigurableContext;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.apache.camel.test.infra.messaging.services.MessagingLocalContainerService;
import org.apache.camel.test.infra.messaging.services.MessagingService;
import org.apache.camel.test.infra.messaging.services.MessagingServiceFactory;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.extension.RegisterExtension;

public abstract class ActiveMQITSupport implements ConfigurableContext, ConfigurableRoute {
    @Order(1)
    @RegisterExtension
    protected static MessagingService service = MessagingServiceFactory.builder()
            .addLocalMapping(ActiveMQITSupport::createLocalService)
            .build();

    @Order(2)
    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();

    public static MessagingLocalContainerService<ArtemisContainer> createLocalService() {
        ArtemisContainer container = new ArtemisContainer();

        return new MessagingLocalContainerService<>(container, c -> container.defaultEndpoint());
    }

    /* We don't want topic advisories here: they may cause publication issues (i.e.:
     * jakarta.jms.InvalidDestinationException: Cannot publish to a deleted Destination) with
     * spring JMS. So, we disable them ...
     */

    public static ActiveMQComponent activeMQComponent(String uri) {
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(uri);

        connectionFactory.setWatchTopicAdvisories(false);

        ActiveMQConfiguration activeMQConfiguration = new ActiveMQConfiguration();
        activeMQConfiguration.setConnectionFactory(connectionFactory);

        return new ActiveMQComponent(activeMQConfiguration);
    }
}
