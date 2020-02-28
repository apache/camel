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
package org.apache.camel.component.paho;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.eclipse.paho.client.mqttv3.MqttClient;

/**
 * Component to integrate with the Eclipse Paho MQTT library.
 */
@Component("paho")
public class PahoComponent extends DefaultComponent {

    @Metadata
    private PahoConfiguration configuration = new PahoConfiguration();

    @Metadata(label = "advanced")
    private MqttClient client;

    public PahoComponent() {
        this(null);
    }
    
    public PahoComponent(CamelContext context) {
        super(context);
        
        registerExtension(new PahoComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Each endpoint can have its own configuration so make
        // a copy of the configuration
        PahoConfiguration configuration = getConfiguration().copy();

        PahoEndpoint answer = new PahoEndpoint(uri, remaining, this, configuration);
        answer.setClient(client);

        setProperties(answer, parameters);
        return answer;
    }

    public PahoConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared Paho configuration
     */
    public void setConfiguration(PahoConfiguration configuration) {
        this.configuration = configuration;
    }

    public MqttClient getClient() {
        return client;
    }

    /**
     * To use a shared Paho client
     */
    public void setClient(MqttClient client) {
        this.client = client;
    }

}
