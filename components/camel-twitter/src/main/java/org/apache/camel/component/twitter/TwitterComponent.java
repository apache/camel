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

import org.apache.camel.CamelContext;
import org.apache.camel.ComponentVerifier;
import org.apache.camel.Endpoint;
import org.apache.camel.VerifiableComponent;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;

/**
 * Twitter component
 */
@Metadata(label = "verifiers", enums = "parameters,connectivity")
public class TwitterComponent extends DefaultComponent implements VerifiableComponent {

    @Metadata(label = "security", secret = true)
    private String consumerKey;
    @Metadata(label = "security", secret = true)
    private String consumerSecret;
    @Metadata(label = "security", secret = true)
    private String accessToken;
    @Metadata(label = "security", secret = true)
    private String accessTokenSecret;
    @Metadata(label = "proxy")
    private String httpProxyHost;
    @Metadata(label = "proxy")
    private String httpProxyUser;
    @Metadata(label = "proxy")
    private String httpProxyPassword;
    @Metadata(label = "proxy")
    private Integer httpProxyPort;

    public TwitterComponent() {
    }

    public TwitterComponent(CamelContext context) {
        super(context);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        TwitterConfiguration properties = new TwitterConfiguration();

        // set options from component
        properties.setConsumerKey(consumerKey);
        properties.setConsumerSecret(consumerSecret);
        properties.setAccessToken(accessToken);
        properties.setAccessTokenSecret(accessTokenSecret);
        properties.setHttpProxyHost(httpProxyHost);
        properties.setHttpProxyUser(httpProxyUser);
        properties.setHttpProxyPassword(httpProxyPassword);
        if (httpProxyPort != null) {
            properties.setHttpProxyPort(httpProxyPort);
        }

        // and then override from parameters
        setProperties(properties, parameters);

        TwitterEndpoint endpoint;

        switch (properties.getType()) {
        case POLLING:
            endpoint = new TwitterEndpointPolling(uri, remaining, this, properties);
            break;
        case EVENT:
            endpoint = new TwitterEndpointEvent(uri,  remaining, this, properties);
            break;
        default:
            endpoint = new TwitterEndpointDirect(uri, remaining, this, properties);
            break;
        }
        return endpoint;
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

    public String getAccessTokenSecret() {
        return accessTokenSecret;
    }

    /**
     * The access token secret
     */
    public void setAccessTokenSecret(String accessTokenSecret) {
        this.accessTokenSecret = accessTokenSecret;
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

    /**
     * The http proxy host which can be used for the camel-twitter.
     */
    public void setHttpProxyHost(String httpProxyHost) {
        this.httpProxyHost = httpProxyHost;
    }

    public String getHttpProxyHost() {
        return httpProxyHost;
    }

    /**
     * The http proxy user which can be used for the camel-twitter.
     */
    public void setHttpProxyUser(String httpProxyUser) {
        this.httpProxyUser = httpProxyUser;
    }

    public String getHttpProxyUser() {
        return httpProxyUser;
    }

    /**
     * The http proxy password which can be used for the camel-twitter.
     */
    public void setHttpProxyPassword(String httpProxyPassword) {
        this.httpProxyPassword = httpProxyPassword;
    }

    public String getHttpProxyPassword() {
        return httpProxyPassword;
    }

    /**
     * The http proxy port which can be used for the camel-twitter.
     */
    public void setHttpProxyPort(int httpProxyPort) {
        this.httpProxyPort = httpProxyPort;
    }

    public int getHttpProxyPort() {
        return httpProxyPort;
    }

    /**
     * Get a verifier for the twitter component.
     */
    public ComponentVerifier getVerifier() {
        return new TwitterComponentVerifier(this);
    }
}
