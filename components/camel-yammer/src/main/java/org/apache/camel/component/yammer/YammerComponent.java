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
package org.apache.camel.component.yammer;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;

/**
 * Represents the component that manages {@link YammerEndpoint}.
 */
public class YammerComponent extends DefaultComponent {

    private String consumerKey;
    private String consumerSecret;
    private String accessToken;
    private YammerConfiguration config;
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // set options from component
        getConfig().setConsumerKey(consumerKey);
        getConfig().setConsumerSecret(consumerSecret);
        getConfig().setAccessToken(accessToken);
        getConfig().setFunction(remaining);
        
        // and then override from parameters
        setProperties(getConfig(), parameters);
        
        Endpoint endpoint = new YammerEndpoint(uri, this, getConfig());
        setProperties(endpoint, parameters);
        return endpoint;
    }
    
    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public YammerConfiguration getConfig() {
        if (config == null) {
            config = new YammerConfiguration();
        }
        return config;
    }

    public void setConfig(YammerConfiguration config) {
        this.config = config;
    }
}
