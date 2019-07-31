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
package org.apache.camel.example.google.pubsub;

import java.io.InputStream;
import java.util.Properties;

import org.apache.camel.component.google.pubsub.GooglePubsubComponent;
import org.apache.camel.component.google.pubsub.GooglePubsubConnectionFactory;

public interface PubsubUtil {

    static GooglePubsubComponent createComponent() {
        GooglePubsubComponent component = new GooglePubsubComponent();
        Properties properties = loadProperties();
        GooglePubsubConnectionFactory connectionFactory = createConnectionFactory(properties);
        component.setConnectionFactory(connectionFactory);
        return component;
    }

    static GooglePubsubConnectionFactory createConnectionFactory(Properties properties) {
        GooglePubsubConnectionFactory connectionFactory = new GooglePubsubConnectionFactory();
        connectionFactory.setCredentialsFileLocation(properties.getProperty("credentials.fileLocation"));
        connectionFactory.setServiceAccount(properties.getProperty("credentials.account"));
        connectionFactory.setServiceAccountKey(properties.getProperty("credentials.key"));
        connectionFactory.setServiceURL(properties.getProperty("pubsub.serviceUrl"));
        return connectionFactory;
    }

    static Properties loadProperties() {
        Properties properties = new Properties();
        InputStream fileIn = PubsubUtil.class.getClassLoader().getResourceAsStream("example.properties");
        try {
            properties.load(fileIn);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return properties;
    }
}
