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
package org.apache.camel.component.stomp;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.UriEndpointComponent;

public class StompComponent extends UriEndpointComponent {

    private StompConfiguration configuration = new StompConfiguration();

    public StompComponent() {
        super(StompEndpoint.class);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String destination = "/" + remaining.replaceAll(":", "/");

        // must copy config so we do not have side effects
        StompConfiguration config = getConfiguration().copy();
        // allow to configure configuration from uri parameters
        setProperties(config, parameters);

        StompEndpoint endpoint = new StompEndpoint(uri, this, config, destination);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public StompConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared stomp configuration
     */
    public void setConfiguration(StompConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * The URI of the Stomp broker to connect to
     */
    public void setBrokerURL(String brokerURL) {
        getConfiguration().setBrokerURL(brokerURL);
    }

    /**
     * The username
     */
    public void setLogin(String login) {
        getConfiguration().setLogin(login);
    }

    /**
     * The password
     */
    public void setPasscode(String passcode) {
        getConfiguration().setPasscode(passcode);
    }
    
    /**
     * The virtual host
     */
    public void setHost(String host) {
        getConfiguration().setHost(host);
    }
}
