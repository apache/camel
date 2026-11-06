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

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Spring finds the session of the transaction of a transacted consumer by the connection factory of its transaction
 * manager. A producer in the same route takes part in that transaction when its template uses the same connection
 * factory instance.
 */
public class ActiveMQCredentialsConnectionFactoryTest {

    @Test
    public void producerUsesTheConnectionFactoryOfTheTransaction() throws Exception {
        ActiveMQComponent activemq = new ActiveMQComponent();
        activemq.setConnectionFactory(new ActiveMQConnectionFactory("tcp://localhost:61616"));

        assertProducerUsesTheConnectionFactoryOfTheTransaction(activemq);
    }

    @Test
    public void producerUsesTheConnectionFactoryOfTheTransactionWithCredentials() throws Exception {
        ActiveMQComponent activemq = new ActiveMQComponent();
        activemq.setConnectionFactory(new ActiveMQConnectionFactory("tcp://localhost:61616"));
        activemq.setUsername("camel");
        activemq.setPassword("rider");

        assertProducerUsesTheConnectionFactoryOfTheTransaction(activemq);
    }

    private static void assertProducerUsesTheConnectionFactoryOfTheTransaction(ActiveMQComponent activemq)
            throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.addComponent("activemq", activemq);
            context.build();
            JmsEndpoint consumer = context.getEndpoint("activemq:queue:in?transacted=true", JmsEndpoint.class);
            JmsEndpoint producer = context.getEndpoint("activemq:queue:out", JmsEndpoint.class);

            ConnectionFactory transaction = ((JmsTransactionManager) consumer.getConfiguration()
                    .getOrCreateTransactionManager()).getConnectionFactory();
            ConnectionFactory template = ((JmsTemplate) producer.createInOnlyTemplate()).getConnectionFactory();
            assertSame(transaction, template);
        }
    }
}
