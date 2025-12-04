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

package org.apache.camel.component.google.drive;

import com.google.api.services.drive.Drive;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiCollection;
import org.apache.camel.component.google.drive.internal.GoogleDriveApiName;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.component.AbstractApiComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("google-drive")
public class GoogleDriveComponent
        extends AbstractApiComponent<GoogleDriveApiName, GoogleDriveConfiguration, GoogleDriveApiCollection> {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleDriveComponent.class);

    @Metadata
    GoogleDriveConfiguration configuration;

    @Metadata(label = "advanced")
    private Drive client;

    @Metadata(label = "advanced")
    private GoogleDriveClientFactory clientFactory;

    @Metadata(label = "proxy", description = "Proxy server host")
    private String proxyHost;

    @Metadata(label = "proxy", description = "Proxy server port")
    private Integer proxyPort;

    public GoogleDriveComponent() {
        super(GoogleDriveApiName.class, GoogleDriveApiCollection.getCollection());
    }

    public GoogleDriveComponent(CamelContext context) {
        super(context, GoogleDriveApiName.class, GoogleDriveApiCollection.getCollection());
    }

    @Override
    protected GoogleDriveApiName getApiName(String apiNameStr) {
        return getCamelContext().getTypeConverter().convertTo(GoogleDriveApiName.class, apiNameStr);
    }

    public Drive getClient(GoogleDriveConfiguration config) {
        if (client == null) {
            if (config.getClientId() != null
                    && !config.getClientId().isBlank()
                    && config.getClientSecret() != null
                    && !config.getClientSecret().isBlank()) {
                client = getClientFactory()
                        .makeClient(
                                config.getClientId(),
                                config.getClientSecret(),
                                config.getScopesAsList(),
                                config.getApplicationName(),
                                config.getRefreshToken(),
                                config.getAccessToken());
            } else if (config.getServiceAccountKey() != null
                    && !config.getServiceAccountKey().isBlank()) {
                client = getClientFactory()
                        .makeClient(
                                getCamelContext(),
                                config.getServiceAccountKey(),
                                config.getScopesAsList(),
                                config.getApplicationName(),
                                config.getDelegate());
            } else {
                throw new IllegalArgumentException(
                        "(clientId and clientSecret) or serviceAccountKey are required to create Google Drive client");
            }
        }
        return client;
    }

    public GoogleDriveClientFactory getClientFactory() {
        if (clientFactory == null) {
            // configure https proxy from camelContext
            if (ObjectHelper.isNotEmpty(getCamelContext().getGlobalOption("http.proxyHost"))
                    && ObjectHelper.isNotEmpty(getCamelContext().getGlobalOption("http.proxyPort"))) {
                String host = getCamelContext().getGlobalOption("http.proxyHost");
                int port = Integer.parseInt(getCamelContext().getGlobalOption("http.proxyPort"));
                LOG.warn(
                        "CamelContext global options [http.proxyHost,http.proxyPort] detected."
                                + " Using global option configuration is deprecated. Instead configure this on the component."
                                + " Using http proxy host: {} port: {}",
                        host,
                        port);

                clientFactory = new BatchGoogleDriveClientFactory(host, port);
            } else if (proxyHost != null && proxyPort != null) {
                clientFactory = new BatchGoogleDriveClientFactory(proxyHost, proxyPort);
            } else {
                clientFactory = new BatchGoogleDriveClientFactory();
            }
        }
        return clientFactory;
    }

    /**
     * To use the shared configuration
     */
    @Override
    public void setConfiguration(GoogleDriveConfiguration configuration) {
        super.setConfiguration(configuration);
    }

    @Override
    public GoogleDriveConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new GoogleDriveConfiguration();
        }
        return super.getConfiguration();
    }

    /**
     * To use the GoogleCalendarClientFactory as factory for creating the client. Will by default use
     * {@link BatchGoogleDriveClientFactory}
     */
    public void setClientFactory(GoogleDriveClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    @Override
    protected Endpoint createEndpoint(
            String uri, String methodName, GoogleDriveApiName apiName, GoogleDriveConfiguration endpointConfiguration) {
        endpointConfiguration.setApiName(apiName);
        endpointConfiguration.setMethodName(methodName);
        GoogleDriveEndpoint endpoint = new GoogleDriveEndpoint(uri, this, apiName, methodName, endpointConfiguration);
        endpoint.setClientFactory(clientFactory);
        return endpoint;
    }
}
