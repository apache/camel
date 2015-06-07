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
package org.apache.camel.component.gae.auth;

import java.security.PrivateKey;

import com.google.gdata.client.authn.oauth.GoogleOAuthHelper;
import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;
import com.google.gdata.client.authn.oauth.OAuthHelper;
import com.google.gdata.client.authn.oauth.OAuthHmacSha1Signer;
import com.google.gdata.client.authn.oauth.OAuthRsaSha1Signer;
import com.google.gdata.client.authn.oauth.OAuthSigner;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Represents a <a href="http://camel.apache.org/gauth.html">GAuth Endpoint</a>.
 * Instances can have one of two names, either <code>authorize</code> for
 * requesting an unauthorized request token or <code>upgrade</code> for
 * upgrading an authorized request token to an access token. The corresponding
 * endpoint URIs are <code>gauth:authorize</code> and <code>gauth:upgrade</code>
 * , respectively.
 */
@UriEndpoint(scheme = "gauth", title = "Google GAuth", syntax = "gauth:name", producerOnly = true, label = "cloud")
public class GAuthEndpoint  extends DefaultEndpoint {

    public static enum Name {
        
        /**
         * Name of the endpoint for requesting an unauthorized request token. 
         */
        AUTHORIZE,

        /**
         * Name of the endpoint for upgrading an authorized request token to an
         * access token.
         */
        UPGRADE
        
    }
    
    private OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> authorizeBinding;
    private OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> upgradeBinding;
    @UriPath @Metadata(required = "true")
    private Name name;
    @UriParam
    private String callback;
    @UriParam
    private String scope;
    @UriParam
    private String consumerKey;
    @UriParam
    private String consumerSecret;
    private GAuthKeyLoader keyLoader;
    private GAuthService service;
    private PrivateKey cachedKey;
    
    public GAuthEndpoint(String endpointUri, Component component, String name) {
        super(endpointUri, component);
        this.name = Name.valueOf(name.toUpperCase());
        this.service = new GAuthServiceImpl(this);
    }
    
    public OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> getAuthorizeBinding() {
        return authorizeBinding;
    }

    /**
     * Sets the binding for <code>gauth:authorize</code> endpoints.  
     */
    public void setAuthorizeBinding(OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> authorizeBinding) {
        this.authorizeBinding = authorizeBinding;
    }

    public OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> getUpgradeBinding() {
        return upgradeBinding;
    }

    /**
     * Sets the binding for <code>gauth:upgrade</code> endpoints. 
     */
    public void setUpgradeBinding(OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> upgradeBinding) {
        this.upgradeBinding = upgradeBinding;
    }

    /**
     * Returns the component instance that created this endpoint.
     */
    public GAuthComponent getComponent() {
        return (GAuthComponent)super.getComponent();
    }

    /**
     * Returns the endpoint name.
     */
    public Name getName() {
        return name;
    }

    /**
     * Returns the value of callback query parameter in the
     * <code>gauth:authorize</code> endpoint URI.
     */
    public String getCallback() {
        return callback;
    }

    public void setCallback(String callback) {
        this.callback = callback;
    }

    /**
     * Returns the value of the scope query parameter in
     * <code>gauth:authorize</code> endpoint URI. This can be a single scope or
     * a comma-separated list of scopes.
     */
    public String getScope() {
        return scope;
    }

    public void setScope(String services) {
        this.scope = services;
    }

    /**
     * Returns the value of the scope query parameter as array.
     * @see #getScope()
     */
    public String[] getScopeArray() {
        return getScope().split(",");
    }

    /**
     * Returns the consumer key. If this endpoint's consumer key is
     * <code>null</code> then {@link GAuthComponent#getConsumerKey()} is
     * returned.
     */
    public String getConsumerKey() {
        if (consumerKey == null) {
            return getComponent().getConsumerKey();
        }
        return consumerKey;
    }

    /**
     * Sets the consumer key. This key is generated when a web application is
     * registered at Google.  
     * 
     * @param consumerKey
     *            consumer key to set.
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    /**
     * Returns the consumer secret. If this endpoint's consumer secret is
     * <code>null</code> then {@link GAuthComponent#getConsumerSecret()} is
     * returned.
     */
    public String getConsumerSecret() {
        if (consumerSecret == null) {
            return getComponent().getConsumerSecret();
        }
        return consumerSecret;
    }

    /**
     * Sets the consumer secret. This secret is generated when a web application
     * is registered at Google. Only set the consumer secret if the HMAC-SHA1 
     * signature method shall be used.
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    /**
     * Returns the key loader. If this endpoint's key loader is
     * <code>null</code> then {@link GAuthComponent#getKeyLoader()} is
     * returned.
     */
    public GAuthKeyLoader getKeyLoader() {
        if (keyLoader == null) {
            return getComponent().getKeyLoader();
        }
        return keyLoader;
    }

    /**
     * Sets a key loader for loading a private key. A private key is required
     * when the RSA-SHA1 signature method shall be used.    
     */
    public void setKeyLoader(GAuthKeyLoader keyLoader) {
        this.keyLoader = keyLoader;
    }

    public GAuthService getService() {
        return service;
    }

    /**
     * Sets the service that makes the remote calls to Google services. Testing
     * code should inject a mock service here (using serviceRef in endpoint
     * URI).
     */
    public void setService(GAuthService service) {
        this.service = service;
    }

    /**
     * @throws UnsupportedOperationException
     */
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("consumption from gauth endpoint not supported");
    }

    /**
     * Returns a {@link GAuthProducer}
     */
    public Producer createProducer() throws Exception {
        return new GAuthProducer(this);
    }

    /**
     * Returns <code>true</code>.
     */
    public boolean isSingleton() {
        return true;
    }

    /**
     * Creates an {@link OAuthHelper} configured with either an
     * {@link OAuthHmacSha1Signer} or an {@link OAuthRsaSha1Signer}, depending
     * on this endpoint's properties.
     */
    OAuthHelper newOAuthHelper() throws Exception {
        OAuthSigner signer = null;
        if (getKeyLoader() == null) {
            signer = new OAuthHmacSha1Signer();
        } else {
            signer = new OAuthRsaSha1Signer(getPrivateKey());
        }
        return new GoogleOAuthHelper(signer);
    }
    
    private synchronized PrivateKey getPrivateKey() throws Exception {
        if (cachedKey == null) {
            cachedKey = getKeyLoader().loadPrivateKey();
        }
        return cachedKey;
    }
    
}
