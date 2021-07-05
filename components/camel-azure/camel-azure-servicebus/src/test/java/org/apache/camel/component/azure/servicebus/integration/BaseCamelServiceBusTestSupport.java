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

import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;
import org.apache.camel.CamelContext;
import org.apache.camel.component.azure.servicebus.ServiceBusComponent;
import org.apache.camel.component.azure.servicebus.ServiceBusTestUtils;
import org.apache.camel.component.azure.servicebus.ServiceBusType;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class BaseCamelServiceBusTestSupport extends CamelTestSupport {

    protected ServiceBusSenderAsyncClient senderAsyncClient;
    protected ServiceBusReceiverAsyncClient receiverAsyncClient;

    @BeforeEach
    void prepareClient() throws Exception {
        senderAsyncClient = ServiceBusTestUtils.createServiceBusSenderAsyncClient(ServiceBusType.topic);
        receiverAsyncClient = ServiceBusTestUtils.createServiceBusReceiverAsyncClient(ServiceBusType.topic);
    }

    @AfterEach
    void closeClient() {
        senderAsyncClient.close();
        receiverAsyncClient.close();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final ServiceBusSenderAsyncClient injectedSenderAsyncClient
                = ServiceBusTestUtils.createServiceBusSenderAsyncClient(ServiceBusType.topic);
        final ServiceBusReceiverAsyncClient injectedReceiverAsyncClient
                = ServiceBusTestUtils.createServiceBusReceiverAsyncClient(ServiceBusType.topic);

        final CamelContext context = super.createCamelContext();
        final ServiceBusComponent component = new ServiceBusComponent(context);

        component.init();
        component.getConfiguration().setReceiverAsyncClient(injectedReceiverAsyncClient);
        component.getConfiguration().setSenderAsyncClient(injectedSenderAsyncClient);
        context.addComponent("azure-servicebus", component);

        return context;
    }
}
