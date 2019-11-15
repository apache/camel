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
package org.apache.camel.component.amqp;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.PropertiesComponent;

import static org.apache.camel.spi.PropertiesComponent.PREFIX_TOKEN;
import static org.apache.camel.spi.PropertiesComponent.SUFFIX_TOKEN;

public class AMQPConnectionDetails {

    public static final String AMQP_HOST = "AMQP_SERVICE_HOST";

    public static final String AMQP_PORT = "AMQP_SERVICE_PORT";

    public static final String AMQP_USERNAME = "AMQP_SERVICE_USERNAME";

    public static final String AMQP_PASSWORD = "AMQP_SERVICE_PASSWORD";
    
    public static final String AMQP_SET_TOPIC_PREFIX = "AMQP_SET_TOPIC_PREFIX";

    private final String uri;

    private final String username;

    private final String password;
    
    private final boolean setTopicPrefix;

    public AMQPConnectionDetails(String uri, String username, String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.setTopicPrefix = true; 
    }
    
    public AMQPConnectionDetails(String uri, String username, String password, boolean setTopicPrefix) {
        this.uri = uri;
        this.username = username;
        this.password = password;
        this.setTopicPrefix = setTopicPrefix;
    }

    public AMQPConnectionDetails(String uri) {
        this(uri, null, null);
    }

    public static AMQPConnectionDetails discoverAMQP(CamelContext camelContext) {
        try {
            PropertiesComponent propertiesComponent = camelContext.getPropertiesComponent();

            String host = property(propertiesComponent, AMQP_HOST, "localhost");
            int port = Integer.parseInt(property(propertiesComponent, AMQP_PORT, "5672"));
            String username = property(propertiesComponent, AMQP_USERNAME, null);
            String password = property(propertiesComponent, AMQP_PASSWORD, null);
            boolean setTopicPrefix = Boolean.parseBoolean(property(propertiesComponent, AMQP_SET_TOPIC_PREFIX, "true"));

            return new AMQPConnectionDetails("amqp://" + host + ":" + port, username, password, setTopicPrefix);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String uri() {
        return uri;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }
    
    public boolean setTopicPrefix() {
        return setTopicPrefix;
    }

    // Helpers

    private static String property(PropertiesComponent propertiesComponent, String key, String defaultValue) {
        try {
            return propertiesComponent.parseUri(PREFIX_TOKEN + key + SUFFIX_TOKEN);
        } catch (IllegalArgumentException e) {
            return defaultValue;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
