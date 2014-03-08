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

public class StompComponent extends DefaultComponent {

    private StompConfiguration configuration = new StompConfiguration();

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

    public void setConfiguration(StompConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setBrokerURL(String brokerURL) {
        getConfiguration().setBrokerURL(brokerURL);
    }

    public void setLogin(String login) {
        getConfiguration().setLogin(login);
    }

    public void setPasscode(String passcode) {
        getConfiguration().setPasscode(passcode);
    }
}
