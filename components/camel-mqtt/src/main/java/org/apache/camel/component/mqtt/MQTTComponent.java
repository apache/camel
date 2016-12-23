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
package org.apache.camel.component.mqtt;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;

/**
 * MQTT Component
 */
public class MQTTComponent extends UriEndpointComponent {
    private String host;
    @Metadata(label = "security", secret = true)
    private String userName;
    @Metadata(label = "security", secret = true)
    private String password;

    public MQTTComponent() {
        super(MQTTEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MQTTConfiguration configuration = new MQTTConfiguration();

        if (host != null) {
            configuration.setHost(host);
        }
        if (userName != null) {
            configuration.setUserName(userName);
        }
        if (password != null) {
            configuration.setPassword(password);
        }

        // and then override from parameters
        setProperties(configuration, parameters);

        MQTTEndpoint endpoint = new MQTTEndpoint(uri, this, configuration);
        endpoint.setName(remaining);
        return endpoint;
    }

    @Override
    public boolean useRawUri() {
        return true; // to prevent MQTT "+" wildcard from being lost
    }

    public String getHost() {
        return host;
    }

    /**
     * The URI of the MQTT broker to connect too - this component also supports SSL - e.g. ssl://127.0.0.1:8883
     */
    public void setHost(String host) {
        this.host = host;
    }

    public String getUserName() {
        return userName;
    }

    /**
     * Username to be used for authentication against the MQTT broker
     */
    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Password to be used for authentication against the MQTT broker
     */
    public void setPassword(String password) {
        this.password = password;
    }
}
