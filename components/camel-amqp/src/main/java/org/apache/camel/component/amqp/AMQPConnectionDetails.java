/**
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
import org.apache.camel.component.properties.PropertiesComponent;

public class AMQPConnectionDetails {

    public static final String AMQP_HOST = "AMQP_SERVICE_HOST";

    public static final String AMQP_PORT = "AMQP_SERVICE_PORT";

    public static final String AMQP_USERNAME = "AMQP_SERVICE_USERNAME";

    public static final String AMQP_PASSWORD = "AMQP_SERVICE_PASSWORD";

    private final String uri;

    private final String username;

    private final String password;

    public AMQPConnectionDetails(String uri, String username, String password) {
        this.uri = uri;
        this.username = username;
        this.password = password;
    }

    public AMQPConnectionDetails(String uri) {
        this(uri, null, null);
    }

    public static AMQPConnectionDetails discoverAMQP(CamelContext camelContext) {
        try {
            PropertiesComponent propertiesComponent = camelContext.getComponent("properties", PropertiesComponent.class);

            String host = property(propertiesComponent, AMQP_HOST, "localhost");
            int port = Integer.parseInt(property(propertiesComponent, AMQP_PORT, "5672"));
            String username = property(propertiesComponent, AMQP_USERNAME, null);
            String password = property(propertiesComponent, AMQP_PASSWORD, null);

            return new AMQPConnectionDetails("amqp://" + host + ":" + port, username, password);
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

    // Helpers

    private static String property(PropertiesComponent propertiesComponent, String key, String defaultValue) {
        try {
            return propertiesComponent.parseUri(propertiesComponent.getPrefixToken() + key + propertiesComponent.getSuffixToken());
        } catch (IllegalArgumentException e) {
            return defaultValue;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}