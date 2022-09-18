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
package org.apache.camel.component.azure.servicebus;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceiverAsyncClient;
import com.azure.messaging.servicebus.ServiceBusSenderAsyncClient;

public final class ServiceBusTestUtils {

    public static final String CONNECTION_STRING = "connectionString";
    public static final String TOPIC_NAME = "topicName";
    public static final String SUBSCRIPTION_NAME = "subscriptionName";
    public static final String QUEUE_NAME = "queueName";

    private ServiceBusTestUtils() {
    }

    public static Properties loadAzurePropertiesFile() throws IOException {
        final Properties properties = new Properties();
        final String fileName = "azure_key.properties";

        final InputStream inputStream
                = Objects.requireNonNull(ServiceBusTestUtils.class.getClassLoader().getResourceAsStream(fileName));

        properties.load(inputStream);

        return properties;
    }

    public static Properties loadAzureAccessFromJvmEnv() throws Exception {
        final Properties properties = new Properties();
        if (System.getProperty(CONNECTION_STRING) == null) {
            throw new Exception(
                    "Make sure to supply azure servicebus connectionString, e.g:  mvn verify -DconnectionString=string");
        }
        properties.setProperty(CONNECTION_STRING, System.getProperty(CONNECTION_STRING));
        properties.setProperty(TOPIC_NAME, System.getProperty(TOPIC_NAME));
        properties.setProperty(SUBSCRIPTION_NAME, System.getProperty(SUBSCRIPTION_NAME));
        //properties.setProperty(QUEUE_NAME, System.getProperty(QUEUE_NAME));

        return properties;
    }

    public static ServiceBusReceiverAsyncClient createServiceBusReceiverAsyncClient(final ServiceBusType type)
            throws Exception {
        final Properties properties = loadAzureAccessFromJvmEnv();

        final ServiceBusClientBuilder.ServiceBusReceiverClientBuilder clientBuilder = new ServiceBusClientBuilder()
                .connectionString(properties.getProperty(CONNECTION_STRING))
                .receiver()
                .subscriptionName(properties.getProperty(SUBSCRIPTION_NAME));

        if (type == ServiceBusType.queue) {
            clientBuilder.queueName(properties.getProperty(QUEUE_NAME));
        } else {
            clientBuilder.topicName(properties.getProperty(TOPIC_NAME));
        }

        return clientBuilder.buildAsyncClient();
    }

    public static ServiceBusSenderAsyncClient createServiceBusSenderAsyncClient(final ServiceBusType type) throws Exception {
        final Properties properties = loadAzureAccessFromJvmEnv();

        final ServiceBusClientBuilder.ServiceBusSenderClientBuilder clientBuilder = new ServiceBusClientBuilder()
                .connectionString(properties.getProperty(CONNECTION_STRING))
                .sender();

        if (type == ServiceBusType.queue) {
            clientBuilder.queueName(properties.getProperty(QUEUE_NAME));
        } else {
            clientBuilder.topicName(properties.getProperty(TOPIC_NAME));
        }

        return clientBuilder.buildAsyncClient();
    }

}
