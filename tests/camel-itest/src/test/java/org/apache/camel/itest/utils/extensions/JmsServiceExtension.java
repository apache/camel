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
package org.apache.camel.itest.utils.extensions;

import jakarta.jms.ConnectionFactory;
import jakarta.jms.JMSException;

import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.itest.CamelJmsTestHelper;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.camel.component.jms.JmsComponent.jmsComponentAutoAcknowledge;
import static org.junit.jupiter.api.Assertions.fail;

public final class JmsServiceExtension implements BeforeAllCallback, AfterAllCallback {
    private static final Logger LOG = LoggerFactory.getLogger(JmsServiceExtension.class);

    private static JmsServiceExtension instance;

    private JmsComponent amq;

    private JmsServiceExtension() throws JMSException {
        LOG.info("Creating a new reusable AMQ component");
        ConnectionFactory connectionFactory = CamelJmsTestHelper.createConnectionFactory();

        amq = jmsComponentAutoAcknowledge(connectionFactory);

        connectionFactory.createConnection();
    }

    @Override
    public void afterAll(ExtensionContext extensionContext) throws Exception {

    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {

    }

    public JmsComponent getComponent() {
        return amq;
    }

    public static synchronized JmsServiceExtension createExtension() {
        if (instance == null) {
            try {
                instance = new JmsServiceExtension();
            } catch (JMSException e) {
                LOG.error("Unable to create JMS connection: {}", e.getMessage(), e);
                fail(String.format("Unable to create JMS connection: %s", e.getMessage()));
            }
        }

        return instance;
    }
}
