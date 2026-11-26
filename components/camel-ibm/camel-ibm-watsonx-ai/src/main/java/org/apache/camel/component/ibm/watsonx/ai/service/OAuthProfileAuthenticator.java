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
package org.apache.camel.component.ibm.watsonx.ai.service;

import java.util.concurrent.CompletableFuture;

import com.ibm.watsonx.ai.core.auth.Authenticator;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;
import org.apache.camel.support.ResolverHelper;

/**
 * An SDK {@link Authenticator} that sends the access token of a Camel OAuth profile as a {@code Bearer} token.
 * <p>
 * The token is asked from the OAuth client factory (camel-oauth) for every request, so it is renewed when it expires:
 * the factory caches it until then (see {@code camel.oauth.<profile>.cache-tokens}). The token is never exchanged at
 * IBM Cloud IAM, as an IBM Cloud API key would be.
 */
public final class OAuthProfileAuthenticator implements Authenticator {

    static final String BEARER = "Bearer";

    private final CamelContext camelContext;
    private final String profileName;
    private final OAuthClientAuthenticationFactory factory;

    public OAuthProfileAuthenticator(CamelContext camelContext, String profileName) {
        this(camelContext, profileName, ResolverHelper.resolveMandatoryBootstrapService(
                camelContext, OAuthClientAuthenticationFactory.FACTORY, OAuthClientAuthenticationFactory.class,
                "camel-oauth"));
    }

    OAuthProfileAuthenticator(CamelContext camelContext, String profileName, OAuthClientAuthenticationFactory factory) {
        this.camelContext = camelContext;
        this.profileName = profileName;
        this.factory = factory;
    }

    @Override
    public String token() {
        try {
            return factory.resolveToken(camelContext, profileName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot obtain an access token for the OAuth profile " + profileName, e);
        }
    }

    @Override
    public CompletableFuture<String> tokenAsync() {
        try {
            return CompletableFuture.completedFuture(token());
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    @Override
    public String scheme() {
        return BEARER;
    }

    public String getProfileName() {
        return profileName;
    }
}
