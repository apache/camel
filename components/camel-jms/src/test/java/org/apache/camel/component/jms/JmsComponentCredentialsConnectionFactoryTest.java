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
package org.apache.camel.component.jms;

import jakarta.jms.ConnectionFactory;

import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jms.connection.JmsTransactionManager;
import org.springframework.jms.connection.UserCredentialsConnectionFactoryAdapter;
import org.springframework.jms.core.JmsTemplate;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The endpoints that wrap the connection factory of the component with the same username and password use the same
 * connection factory, by which Spring finds the session of a transaction.
 */
public class JmsComponentCredentialsConnectionFactoryTest {

    private final ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://0");
    private CamelContext context;
    private JmsComponent jms;

    @BeforeEach
    void createContext() {
        context = new DefaultCamelContext();
        jms = new JmsComponent();
        jms.setConnectionFactory(connectionFactory);
        context.addComponent("jms", jms);
        context.build();
    }

    @AfterEach
    void closeContext() throws Exception {
        context.close();
    }

    private ConnectionFactory connectionFactory(String uri) {
        return context.getEndpoint(uri, JmsEndpoint.class).getConfiguration().getConnectionFactory();
    }

    @Test
    public void credentialsOfTheComponentGiveOneConnectionFactory() {
        jms.setUsername("camel");
        jms.setPassword("rider");

        JmsEndpoint consumer = context.getEndpoint("jms:queue:in?transacted=true", JmsEndpoint.class);
        JmsEndpoint producer = context.getEndpoint("jms:queue:out", JmsEndpoint.class);

        ConnectionFactory transactionKey
                = ((JmsTransactionManager) consumer.getConfiguration().getOrCreateTransactionManager()).getConnectionFactory();
        ConnectionFactory listenerKey = consumer.getConfiguration().getOrCreateListenerConnectionFactory();
        ConnectionFactory producerKey = ((JmsTemplate) producer.createInOnlyTemplate()).getConnectionFactory();
        assertInstanceOf(UserCredentialsConnectionFactoryAdapter.class, transactionKey);
        assertSame(transactionKey, listenerKey);
        assertSame(transactionKey, producerKey);
    }

    @Test
    public void sameCredentialsOnTheEndpointsGiveOneConnectionFactory() {
        ConnectionFactory in = connectionFactory("jms:queue:in?username=camel&password=rider");
        ConnectionFactory out = connectionFactory("jms:queue:out?username=camel&password=rider");

        assertInstanceOf(UserCredentialsConnectionFactoryAdapter.class, in);
        assertSame(in, out);
    }

    @Test
    public void otherCredentialsGiveTheirOwnConnectionFactory() {
        jms.setUsername("camel");
        jms.setPassword("rider");

        ConnectionFactory component = connectionFactory("jms:queue:in");
        ConnectionFactory otherUser = connectionFactory("jms:queue:out?username=other&password=rider");
        ConnectionFactory otherPassword = connectionFactory("jms:queue:out?username=camel&password=other");

        assertNotSame(component, otherUser);
        assertNotSame(component, otherPassword);
        assertNotSame(otherUser, otherPassword);
    }

    @Test
    public void newPasswordGivesANewConnectionFactory() {
        jms.setUsername("camel");
        jms.setPassword("rider");
        ConnectionFactory before = connectionFactory("jms:queue:before");

        // for example the routes are created again after the password was rotated
        jms.setPassword("rotated");
        ConnectionFactory after = connectionFactory("jms:queue:after");
        ConnectionFactory afterToo = connectionFactory("jms:queue:afterToo");

        assertNotSame(before, after);
        assertSame(after, afterToo);
    }

    @Test
    public void connectionFactoryOfTheEndpointGetsItsOwnAdapter() {
        context.getRegistry().bind("endpointFactory", new ActiveMQConnectionFactory("vm://0"));
        jms.setUsername("camel");
        jms.setPassword("rider");

        ConnectionFactory component = connectionFactory("jms:queue:in");
        ConnectionFactory endpoint = connectionFactory("jms:queue:out?connectionFactory=#endpointFactory");

        assertInstanceOf(UserCredentialsConnectionFactoryAdapter.class, endpoint);
        assertNotSame(component, endpoint);
    }
}
