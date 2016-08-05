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
package org.apache.camel.component.linkedin;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Map;
import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.linkedin.api.LinkedInOAuthRequestFilter;
import org.apache.camel.component.linkedin.api.OAuthParams;
import org.apache.camel.component.linkedin.api.OAuthScope;
import org.apache.camel.component.linkedin.api.OAuthSecureStorage;
import org.apache.camel.component.linkedin.internal.CachingOAuthSecureStorage;
import org.apache.camel.component.linkedin.internal.LinkedInApiCollection;
import org.apache.camel.component.linkedin.internal.LinkedInApiName;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.component.AbstractApiComponent;
import org.apache.camel.util.jsse.SSLContextParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link LinkedInEndpoint}.
 */
public class LinkedInComponent extends AbstractApiComponent<LinkedInApiName, LinkedInConfiguration, LinkedInApiCollection> {

    private static final Logger LOG = LoggerFactory.getLogger(LinkedInComponent.class);

    private LinkedInOAuthRequestFilter requestFilter;

    public LinkedInComponent() {
        super(LinkedInEndpoint.class, LinkedInApiName.class, LinkedInApiCollection.getCollection());
    }

    public LinkedInComponent(CamelContext context) {
        super(context, LinkedInEndpoint.class, LinkedInApiName.class, LinkedInApiCollection.getCollection());
    }

    @Override
    protected LinkedInApiName getApiName(String apiNameStr) throws IllegalArgumentException {
        return LinkedInApiName.fromValue(apiNameStr);
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(LinkedInConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    /**
     * To use the shared configuration. Properties of the shared configuration can also be set individually.
     */
    @Override
    public LinkedInConfiguration getConfiguration() {
        return super.getConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String methodName, LinkedInApiName apiName,
                                      LinkedInConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        return new LinkedInEndpoint(uri, this, apiName, methodName, endpointConfiguration);
    }

    public synchronized LinkedInOAuthRequestFilter getRequestFilter(LinkedInConfiguration endpointConfiguration) {
        if (endpointConfiguration.equals(configuration)) {
            if (requestFilter == null) {
                requestFilter = createRequestFilter(this.configuration);
            }
            return requestFilter;
        } else {
            return createRequestFilter(endpointConfiguration);
        }
    }

    private LinkedInOAuthRequestFilter createRequestFilter(LinkedInConfiguration configuration) {
        // validate configuration
        configuration.validate();

        final String[] enabledProtocols;
        try {
            // use default SSP to create supported non-SSL protocols list
            final SSLContext sslContext = new SSLContextParameters().createSSLContext(getCamelContext());
            enabledProtocols = sslContext.createSSLEngine().getEnabledProtocols();
        } catch (GeneralSecurityException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        } catch (IOException e) {
            throw ObjectHelper.wrapRuntimeCamelException(e);
        }
        return new LinkedInOAuthRequestFilter(getOAuthParams(configuration),
                configuration.getHttpParams(), configuration.isLazyAuth(), enabledProtocols);
    }

    private static OAuthParams getOAuthParams(LinkedInConfiguration configuration) {
        return new OAuthParams(configuration.getUserName(), configuration.getUserPassword(),
                new CachingOAuthSecureStorage(configuration.getSecureStorage()), configuration.getClientId(), configuration.getClientSecret(),
                configuration.getRedirectUri(), configuration.getScopes());
    }

    @Override
    protected void doStop() throws Exception {
        if (requestFilter != null) {
            closeLogException(requestFilter);
        }
    }

    protected void closeRequestFilter(LinkedInOAuthRequestFilter requestFilter) {
        // only close if not a shared filter
        if (this.requestFilter != requestFilter) {
            closeLogException(requestFilter);
        }
    }

    private void closeLogException(LinkedInOAuthRequestFilter requestFilter) {
        try {
            requestFilter.close();
        } catch (Exception e) {
            LOG.warn("Error closing OAuth2 request filter: " + e.getMessage(), e);
        }
    }

    private LinkedInConfiguration getConfigurationOrCreate() {
        if (this.getConfiguration() == null) {
            this.setConfiguration(new LinkedInConfiguration());
        }
        return this.getConfiguration();
    }

    public LinkedInApiName getApiName() {
        return getConfigurationOrCreate().getApiName();
    }

    /**
     * What kind of operation to perform
     * @param apiName
     */
    public void setApiName(LinkedInApiName apiName) {
        getConfigurationOrCreate().setApiName(apiName);
    }

    public String getMethodName() {
        return getConfigurationOrCreate().getMethodName();
    }

    /**
     * What sub operation to use for the selected operation
     * @param methodName
     */
    public void setMethodName(String methodName) {
        getConfigurationOrCreate().setMethodName(methodName);
    }

    public String getUserName() {
        return getConfigurationOrCreate().getUserName();
    }

    /**
     * LinkedIn user account name, MUST be provided
     * @param userName
     */
    public void setUserName(String userName) {
        getConfigurationOrCreate().setUserName(userName);
    }

    public String getUserPassword() {
        return getConfigurationOrCreate().getUserPassword();
    }

    /**
     * LinkedIn account password
     * @param userPassword
     */
    public void setUserPassword(String userPassword) {
        getConfigurationOrCreate().setUserPassword(userPassword);
    }

    public OAuthSecureStorage getSecureStorage() {
        return getConfigurationOrCreate().getSecureStorage();
    }

    /**
     * Callback interface for providing an OAuth token or to store the token generated by the component.
     * The callback should return null on the first call and then save the created token in the saveToken() callback.
     * If the callback returns null the first time, a userPassword MUST be provided
     * @param secureStorage
     */
    public void setSecureStorage(OAuthSecureStorage secureStorage) {
        getConfigurationOrCreate().setSecureStorage(secureStorage);
    }

    public String getClientId() {
        return getConfigurationOrCreate().getClientId();
    }

    /**
     * LinkedIn application client ID
     * @param clientId
     */
    public void setClientId(String clientId) {
        getConfigurationOrCreate().setClientId(clientId);
    }

    public String getClientSecret() {
        return getConfigurationOrCreate().getClientSecret();
    }

    /**
     * LinkedIn application client secret
     * @param clientSecret
     */
    public void setClientSecret(String clientSecret) {
        getConfigurationOrCreate().setClientSecret(clientSecret);
    }

    public OAuthScope[] getScopes() {
        return getConfigurationOrCreate().getScopes();
    }

    /**
     * List of LinkedIn scopes as specified at https://developer.linkedin.com/documents/authentication#granting
     * @param scopes
     */
    public void setScopes(OAuthScope[] scopes) {
        getConfigurationOrCreate().setScopes(scopes);
    }

    public String getRedirectUri() {
        return getConfigurationOrCreate().getRedirectUri();
    }

    /**
     * Application redirect URI, although the component never redirects to this page to avoid having to have a functioning redirect server.
     * So for testing one could use https://localhost
     * @param redirectUri
     */
    public void setRedirectUri(String redirectUri) {
        getConfigurationOrCreate().setRedirectUri(redirectUri);
    }

    public Map<String, Object> getHttpParams() {
        return getConfigurationOrCreate().getHttpParams();
    }

    /**
     * Custom HTTP params, for example proxy host and port, use constants from AllClientPNames
     * @param httpParams
     */
    public void setHttpParams(Map<String, Object> httpParams) {
        getConfigurationOrCreate().setHttpParams(httpParams);
    }

    public boolean isLazyAuth() {
        return getConfigurationOrCreate().isLazyAuth();
    }

    /**
     * Flag to enable/disable lazy OAuth, default is true. when enabled, OAuth token retrieval or generation is not done until the first REST call
     * @param lazyAuth
     */
    public void setLazyAuth(boolean lazyAuth) {
        getConfigurationOrCreate().setLazyAuth(lazyAuth);
    }
}
