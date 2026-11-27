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
package org.apache.camel.component.ibm.watsonx.data;

import com.ibm.cloud.sdk.core.security.Authenticator;
import okhttp3.Request;
import org.apache.camel.CamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;
import org.apache.camel.support.ResolverHelper;

/**
 * An authenticator of the IBM Cloud SDK that sends the access token of a Camel OAuth profile as a {@code Bearer} token.
 * <p>
 * The token is asked from the OAuth client factory (camel-oauth) for every request, so it is renewed when it expires:
 * the factory caches it until then (see {@code camel.oauth.<profile>.cache-tokens}). The token is never exchanged at
 * IBM Cloud IAM, as an IBM Cloud API key would be.
 */
public final class OAuthProfileAuthenticator implements Authenticator {

    private static final String AUTHORIZATION = "Authorization";

    private final CamelContext camelContext;
    private final String profileName;
    private final OAuthClientAuthenticationFactory factory;

    public OAuthProfileAuthenticator(CamelContext camelContext, String profileName) {
        this.camelContext = camelContext;
        this.profileName = profileName;
        this.factory = ResolverHelper.resolveMandatoryBootstrapService(
                camelContext, OAuthClientAuthenticationFactory.FACTORY, OAuthClientAuthenticationFactory.class,
                "camel-oauth");
    }

    /**
     * The access token of the profile.
     *
     * @throws IllegalStateException when the profile gives no token
     */
    public String token() {
        String token;
        try {
            token = factory.resolveToken(camelContext, profileName);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeCamelException("Cannot obtain an access token for the OAuth profile " + profileName, e);
        }
        if (token == null || token.isBlank()) {
            throw new IllegalStateException("The OAuth profile " + profileName + " gave no access token");
        }
        return token;
    }

    @Override
    public void validate() {
        // the endpoint asks for a token when it starts
    }

    @Override
    public String authenticationType() {
        return Authenticator.AUTHTYPE_BEARER_TOKEN;
    }

    @Override
    public void authenticate(Request.Builder requestBuilder) {
        requestBuilder.header(AUTHORIZATION, "Bearer " + token());
    }

    public String getProfileName() {
        return profileName;
    }
}
