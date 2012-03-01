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

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.gae.bind.OutboundBinding;
import org.apache.camel.impl.DefaultComponent;

/**
 * The <a href="http://camel.apache.org/gauth.html">GAuth Component</a>
 * implements a Google-specific OAuth comsumer. This component supports OAuth
 * 1.0a. For background information refer to <a
 * href="http://code.google.com/apis/accounts/docs/OAuth.html">OAuth for Web
 * Applications</a> and the <a
 * href="http://code.google.com/apis/gdata/docs/auth/oauth.html">GData developer
 * guide for OAuth.</a>.
 */
public class GAuthComponent extends DefaultComponent {

    private String consumerKey;
    
    private String consumerSecret;

    private GAuthKeyLoader keyLoader;

    public GAuthComponent() {
        this(null);
    }

    public GAuthComponent(CamelContext context) {
        super(context);
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

    public GAuthKeyLoader getKeyLoader() {
        return keyLoader;
    }

    public void setKeyLoader(GAuthKeyLoader keyLoader) {
        this.keyLoader = keyLoader;
    }

    @Override
    public GAuthEndpoint createEndpoint(String uri) throws Exception {
        return (GAuthEndpoint)super.createEndpoint(uri);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        GAuthEndpoint endpoint = new GAuthEndpoint(uri, this, remaining);
        OutboundBinding authorizeBinding = resolveAndRemoveReferenceParameter(
                parameters, "authorizeBindingRef", GAuthAuthorizeBinding.class, new GAuthAuthorizeBinding());
        OutboundBinding upgradeBinding = resolveAndRemoveReferenceParameter(
                parameters, "upgradeBindingRef", GAuthUpgradeBinding.class, new GAuthUpgradeBinding());
        GAuthService service = resolveAndRemoveReferenceParameter(
                parameters, "serviceRef", GAuthService.class, new GAuthServiceImpl(endpoint));
        GAuthKeyLoader keyLoader = resolveAndRemoveReferenceParameter(
                parameters, "keyLoaderRef", GAuthKeyLoader.class);
        endpoint.setAuthorizeBinding(authorizeBinding);
        endpoint.setUpgradeBinding(upgradeBinding);
        endpoint.setService(service);
        endpoint.setKeyLoader(keyLoader);
        return endpoint;
    }

}
