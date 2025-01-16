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
package org.apache.camel.component.azure.servicebus.integration;

import java.util.List;
import java.util.concurrent.CountDownLatch;

import com.azure.core.exception.ResourceExistsException;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClient;
import com.azure.messaging.servicebus.administration.ServiceBusAdministrationClientBuilder;
import com.azure.messaging.servicebus.administration.models.CreateQueueOptions;
import com.azure.messaging.servicebus.administration.models.CreateSubscriptionOptions;
import com.azure.messaging.servicebus.administration.models.CreateTopicOptions;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.azure.servicebus.ServiceBusComponent;
import org.apache.camel.test.infra.core.CamelContextExtension;
import org.apache.camel.test.infra.core.DefaultCamelContextExtension;
import org.apache.camel.test.infra.core.annotations.ContextFixture;
import org.apache.camel.test.infra.core.annotations.RouteFixture;
import org.apache.camel.test.infra.core.api.ConfigurableRoute;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.System.getProperty;

public abstract class BaseServiceBusTestSupport implements ConfigurableRoute {
    public static final String CONNECTION_STRING_PROPERTY_NAME = "camel.component.azure-servicebus.connection-string";
    protected static final String CONNECTION_STRING = getProperty(CONNECTION_STRING_PROPERTY_NAME);
    protected static final String QUEUE_NAME = "camelTestQueue";
    protected static final String QUEUE_WITH_SESSIONS_NAME = "camelTestQueueSessions";
    protected static final String TOPIC_NAME = "camelTestTopic";
    protected static final String TOPIC_WITH_SESSIONS_NAME = "camelTestTopicSessions";
    protected static final String SUBSCRIPTION_NAME = "camelTestSubscription";
    protected static final String SUBSCRIPTION_WITH_SESSIONS_NAME = "camelTestSubscriptionSessions";
    protected static final String MOCK_RESULT = "mock:result";
    private static final Logger LOGGER = LoggerFactory.getLogger(BaseServiceBusTestSupport.class);
    @RegisterExtension
    protected static CamelContextExtension contextExtension = new DefaultCamelContextExtension();
    protected static ServiceBusAdministrationClient serviceBusAdminClient;
    protected CountDownLatch messageLatch;
    protected List<ServiceBusReceivedMessageContext> receivedMessageContexts;

    @BeforeAll
    static void baseBeforeAll() {
        serviceBusAdminClient = new ServiceBusAdministrationClientBuilder()
                .connectionString(CONNECTION_STRING)
                .buildClient();
        try {
            serviceBusAdminClient.createQueue(QUEUE_NAME);
            serviceBusAdminClient.createQueue(QUEUE_WITH_SESSIONS_NAME, new CreateQueueOptions().setSessionRequired(true));
        } catch (ResourceExistsException e) {
            LOGGER.warn("Test queue already existed", e);
        }
        try {
            serviceBusAdminClient.createTopic(TOPIC_NAME);
            serviceBusAdminClient.createTopic(TOPIC_WITH_SESSIONS_NAME, new CreateTopicOptions().setSessionRequired(true));
        } catch (ResourceExistsException e) {
            LOGGER.warn("Test topic already existed", e);
        }
        try {
            serviceBusAdminClient.createSubscription(TOPIC_NAME, SUBSCRIPTION_NAME);
            serviceBusAdminClient.createSubscription(TOPIC_WITH_SESSIONS_NAME, SUBSCRIPTION_WITH_SESSIONS_NAME,
                    new CreateSubscriptionOptions().setSessionRequired(true));
        } catch (ResourceExistsException e) {
            LOGGER.warn("Test subscription already existed", e);
        }
    }

    @AfterAll
    static void baseAfterAll() {
        serviceBusAdminClient.deleteSubscription(TOPIC_NAME, SUBSCRIPTION_NAME);
        serviceBusAdminClient.deleteSubscription(TOPIC_WITH_SESSIONS_NAME, SUBSCRIPTION_WITH_SESSIONS_NAME);
        serviceBusAdminClient.deleteTopic(TOPIC_NAME);
        serviceBusAdminClient.deleteTopic(TOPIC_WITH_SESSIONS_NAME);
        serviceBusAdminClient.deleteQueue(QUEUE_NAME);
        serviceBusAdminClient.deleteQueue(QUEUE_WITH_SESSIONS_NAME);
    }

    @ContextFixture
    public void configureServiceBus(CamelContext context) {
        ServiceBusComponent serviceBusComponent = new ServiceBusComponent(context);
        serviceBusComponent.init();
        serviceBusComponent.getConfiguration().setConnectionString(CONNECTION_STRING);
        context.addComponent("azure-servicebus", serviceBusComponent);
    }

    @RouteFixture
    public void createRouteBuilder(CamelContext context) throws Exception {
        context.addRoutes(createRouteBuilder());
    }

    protected abstract RouteBuilder createRouteBuilder();

    protected ServiceBusProcessorClient createQueueProcessorClient() {
        return new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .processor()
                .queueName(QUEUE_NAME)
                .processMessage(this::processMessage)
                .processError(serviceBusErrorContext -> LOGGER.error("Service Bus client error",
                        serviceBusErrorContext.getException()))
                .buildProcessorClient();
    }

    protected ServiceBusProcessorClient createSessionQueueProcessorClient() {
        return new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .sessionProcessor()
                .queueName(QUEUE_WITH_SESSIONS_NAME)
                .processMessage(this::processMessage)
                .processError(serviceBusErrorContext -> LOGGER.error("Service Bus client error",
                        serviceBusErrorContext.getException()))
                .buildProcessorClient();
    }

    protected ServiceBusProcessorClient createTopicProcessorClient() {
        return new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .processor()
                .topicName(TOPIC_NAME)
                .subscriptionName(SUBSCRIPTION_NAME)
                .processMessage(this::processMessage)
                .processError(serviceBusErrorContext -> LOGGER.error("Service Bus client error",
                        serviceBusErrorContext.getException()))
                .buildProcessorClient();
    }

    protected ServiceBusProcessorClient createTopicSessionProcessorClient() {
        return new ServiceBusClientBuilder()
                .connectionString(CONNECTION_STRING)
                .sessionProcessor()
                .disableAutoComplete()
                .topicName(TOPIC_WITH_SESSIONS_NAME)
                .subscriptionName(SUBSCRIPTION_WITH_SESSIONS_NAME)
                .processMessage(this::processMessage)
                .processError(serviceBusErrorContext -> LOGGER.error("Service Bus client error",
                        serviceBusErrorContext.getException()))
                .buildProcessorClient();
    }

    private void processMessage(ServiceBusReceivedMessageContext messageContext) {
        receivedMessageContexts.add(messageContext);
        messageLatch.countDown();
    }
}
