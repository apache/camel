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
package org.apache.camel.component.springrabbit.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.component.springrabbit.SpringRabbitMQComponent;
import org.apache.camel.component.springrabbit.test.infra.services.RabbitMQServiceFactory;
import org.apache.camel.test.infra.rabbitmq.services.RabbitMQService;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class RabbitMQITSupport extends CamelTestSupport {

    @RegisterExtension
    public static RabbitMQService service = RabbitMQServiceFactory.createService();

    protected Logger log = LoggerFactory.getLogger(getClass());

    ConnectionFactory createConnectionFactory(boolean confirm) {
        CachingConnectionFactory cf = new CachingConnectionFactory();
        if (confirm) {
            cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        }
        cf.setUri(service.getAmqpUrl());
        return cf;
    }

    protected boolean confirmEnabled() {
        return false;
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        context.getRegistry().bind("myCF", createConnectionFactory(confirmEnabled()));

        SpringRabbitMQComponent rmq = context.getComponent("spring-rabbitmq", SpringRabbitMQComponent.class);
        // turn on auto declare
        rmq.setAutoDeclare(true);

        return context;
    }
}
