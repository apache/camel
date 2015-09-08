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

import java.util.Map;

import com.google.gdata.client.authn.oauth.GoogleOAuthParameters;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.UriEndpointComponent;

/**
 * The <a href="http://camel.apache.org/gauth.html">GAuth Component</a>
 * implements a Google-specific OAuth comsumer. This component supports OAuth
 * 1.0a. For background information refer to <a
 * href="http://code.google.com/apis/accounts/docs/OAuth.html">OAuth for Web
 * Applications</a> and the <a
 * href="http://code.google.com/apis/gdata/docs/auth/oauth.html">GData developer
 * guide for OAuth.</a>.
 */
public class GAuthComponent extends UriEndpointComponent {

    private String consumerKey;
    private String consumerSecret;
    private GAuthKeyLoader keyLoader;

    public GAuthComponent() {
        super(GAuthEndpoint.class);
    }

    public GAuthComponent(CamelContext context) {
        super(context, GAuthEndpoint.class);
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    /**
     * Domain identifying the web application. This is the domain used when registering the application with Google.
     * Example: camelcloud.appspot.com. For a non-registered application use anonymous.
     */
    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getConsumerSecret() {
        return consumerSecret;
    }

    /**
     * Consumer secret of the web application. The consumer secret is generated when when registering the application with Google.
     * It is needed if the HMAC-SHA1 signature method shall be used. For a non-registered application use anonymous.
     */
    public void setConsumerSecret(String consumerSecret) {
        this.consumerSecret = consumerSecret;
    }

    public GAuthKeyLoader getKeyLoader() {
        return keyLoader;
    }

    /**
     * To configure a key loader to use.
     * Part of camel-gae are two key loaders: GAuthPk8Loader for loading a private key from a PKCS#8 file and GAuthJksLoader to load a private key from a Java key store.
     * It is needed if the RSA-SHA1 signature method shall be used. These classes are defined in the org.apache.camel.component.gae.auth package.
     */
    public void setKeyLoader(GAuthKeyLoader keyLoader) {
        this.keyLoader = keyLoader;
    }

    @Override
    public GAuthEndpoint createEndpoint(String uri) throws Exception {
        return (GAuthEndpoint)super.createEndpoint(uri);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        GAuthEndpoint endpoint = new GAuthEndpoint(uri, this, remaining);
        OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> authorizeBinding = resolveAndRemoveReferenceParameter(
                parameters, "authorizeBindingRef", GAuthAuthorizeBinding.class, new GAuthAuthorizeBinding());
        OutboundBinding<GAuthEndpoint, GoogleOAuthParameters, GoogleOAuthParameters> upgradeBinding = resolveAndRemoveReferenceParameter(
                parameters, "upgradeBindingRef", GAuthUpgradeBinding.class, new GAuthUpgradeBinding());
        GAuthService service = resolveAndRemoveReferenceParameter(
                parameters, "serviceRef", GAuthService.class, new GAuthServiceImpl(endpoint));
        GAuthKeyLoader keyLoader = resolveAndRemoveReferenceParameter(
                parameters, "keyLoaderRef", GAuthKeyLoader.class);
        endpoint.setAuthorizeBinding(authorizeBinding);
        endpoint.setUpgradeBinding(upgradeBinding);
        endpoint.setService(service);
        // ensure to inject CamelContext to key loader
        if (keyLoader != null) {
            keyLoader.setCamelContext(getCamelContext());
        }
        endpoint.setKeyLoader(keyLoader);
        return endpoint;
    }

}
