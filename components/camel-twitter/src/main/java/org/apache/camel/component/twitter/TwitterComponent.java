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
package org.apache.camel.component.twitter;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.twitter.data.EndpointType;
import org.apache.camel.impl.DefaultComponent;

/**
 * Twitter component
 */
public class TwitterComponent extends DefaultComponent {

    private String consumerKey;
    private String consumerSecret;
    private String accessToken;
    private String accessTokenSecret;

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
        throws Exception {

        TwitterConfiguration properties = new TwitterConfiguration();

        // set options from component
        properties.setConsumerKey(consumerKey);
        properties.setConsumerSecret(consumerSecret);
        properties.setAccessToken(accessToken);
        properties.setAccessTokenSecret(accessTokenSecret);

        // and then override from parameters
        setProperties(properties, parameters);

        TwitterEndpoint endpoint;
        switch (EndpointType.fromUri(properties.getType())) {
        case POLLING:
            endpoint = new TwitterEndpointPolling(uri, this, properties);
            break;
        case EVENT:
            endpoint = new TwitterEndpointEvent(uri, this, properties);
            break;
        default:
            endpoint = new TwitterEndpointDirect(uri, this, properties);
            break;
        }
        return endpoint;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
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
}
