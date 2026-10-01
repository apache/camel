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
package org.apache.camel.component.odata;

import java.net.URI;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.jsse.SSLContextParameters;

@UriEndpoint(
             firstVersion = "4.23.0",
             scheme = "odata",
             title = "OData",
             syntax = "odata:httpUri",
             producerOnly = true,
             lenientProperties = true,
             category = { Category.HTTP, Category.CLOUD },
             headersClass = ODataConstants.class)
public class ODataEndpoint extends DefaultEndpoint implements EndpointServiceLocation, SSLContextParametersAware {

    @UriPath
    @Metadata(required = true, description = "The base OData service URI")
    private URI httpUri;

    @UriParam
    private ODataConfiguration configuration = new ODataConfiguration();

    @UriParam(label = "security", defaultValue = "false",
              description = "Enable usage of global SSL context parameters")
    private boolean useGlobalSslContextParameters;

    private final Map<String, Object> odataQueryParams = new LinkedHashMap<>();

    public ODataEndpoint(String uri, Component component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() throws Exception {
        return new ODataProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
                "You cannot receive messages at this endpoint: " + getEndpointUri());
    }

    @Override
    public String getServiceUrl() {
        return httpUri != null ? httpUri.toString() : null;
    }

    @Override
    public String getServiceProtocol() {
        if (httpUri != null && httpUri.getScheme() != null) {
            return httpUri.getScheme();
        }
        return "http";
    }

    @Override
    public Map<String, String> getServiceMetadata() {
        if (httpUri != null) {
            Map<String, String> metadata = new HashMap<>();
            metadata.put("url", httpUri.toString());
            return metadata;
        }
        return null;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public ODataConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(ODataConfiguration configuration) {
        this.configuration = configuration;
    }

    public Map<String, Object> getOdataQueryParams() {
        return odataQueryParams;
    }

    public void setOdataQueryParams(Map<String, Object> params) {
        odataQueryParams.clear();
        if (params != null) {
            odataQueryParams.putAll(params);
        }
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration != null ? configuration.getSslContextParameters() : null;
    }

    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        if (configuration != null) {
            configuration.setSslContextParameters(sslContextParameters);
        }
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }
}
