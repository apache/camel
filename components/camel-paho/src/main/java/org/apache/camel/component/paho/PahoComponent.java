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
package org.apache.camel.component.paho;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;

/**
 * Component to integrate with the Eclispe Paho MQTT library.
 */
public class PahoComponent extends UriEndpointComponent {

    private String brokerUrl;
    private String clientId;
    @Metadata(label = "advanced")
    private MqttConnectOptions connectOptions;
    
    public PahoComponent() {
        super(PahoEndpoint.class);
    }

    // Overridden

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        PahoEndpoint answer = new PahoEndpoint(uri, remaining, this);

        if (brokerUrl != null) {
            answer.setBrokerUrl(brokerUrl);
        }
        if (clientId != null) {
            answer.setClientId(clientId);
        }
        if (connectOptions != null) {
            answer.setConnectOptions(connectOptions);
        }

        setProperties(answer, parameters);
        return answer;
    }

    // Getters and setters

    public String getBrokerUrl() {
        return brokerUrl;
    }

    /**
     * The URL of the MQTT broker.
     */
    public void setBrokerUrl(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    public String getClientId() {
        return clientId;
    }

    /**
     * MQTT client identifier.
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public MqttConnectOptions getConnectOptions() {
        return connectOptions;
    }

    /**
     * Client connection options
     */
    public void setConnectOptions(MqttConnectOptions connectOptions) {
        this.connectOptions = connectOptions;
    }
    
}