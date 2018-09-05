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
import org.apache.camel.spi.Metadata;

/**
 * Represents the component that manages {@link YammerEndpoint}.
 */
public class YammerComponent extends DefaultComponent {

    @Metadata(label = "security", secret = true)
    private String consumerKey;
    @Metadata(label = "security", secret = true)
    private String consumerSecret;
    @Metadata(label = "security", secret = true)
    private String accessToken;
    @Metadata(label = "advanced")
    private YammerConfiguration config;
    
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // by default use config for each endpoint; use from component level if one has been explicitly set
        YammerConfiguration endpointConfig = getConfig();
        if (endpointConfig == null) {
            endpointConfig = new YammerConfiguration();            
        }
        
        // set options from component
        endpointConfig.setConsumerKey(consumerKey);
        endpointConfig.setConsumerSecret(consumerSecret);
        endpointConfig.setAccessToken(accessToken);
        endpointConfig.setFunction(remaining);
        endpointConfig.setFunctionType(YammerFunctionType.fromUri(remaining));
        
        // and then override from parameters
        setProperties(endpointConfig, parameters);
        
        Endpoint endpoint = new YammerEndpoint(uri, this, endpointConfig);
        setProperties(endpoint, parameters);
        return endpoint;
    }
    
    public String getConsumerKey() {
        return consumerKey;
    }

    /**
     * The consumer key
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    /**
     * The consumer secret
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public String getAccessToken() {
        return accessToken;
    }

    /**
     * The access token
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public YammerConfiguration getConfig() {
        return config;
    }

    /**
     * To use a shared yammer configuration
     */
    public void setConfig(YammerConfiguration config) {
        this.config = config;
    }
}
