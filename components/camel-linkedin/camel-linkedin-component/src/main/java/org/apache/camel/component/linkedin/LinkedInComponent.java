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
import javax.net.ssl.SSLContext;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.linkedin.api.LinkedInOAuthRequestFilter;
import org.apache.camel.component.linkedin.api.OAuthParams;
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
     * To use the shared configuration
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
}
