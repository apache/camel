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
package org.apache.camel.component.mina;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ExchangePattern;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.mina.core.filterchain.IoFilter;

/**
 * Component for Apache MINA 2.x.
 */
@Component("mina")
public class MinaComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced")
    private MinaConfiguration configuration;
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public MinaComponent() {
    }

    public MinaComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // Using the configuration which set by the component as a default one
        // Since the configuration's properties will be set by the URI
        // we need to copy or create a new MinaConfiguration here
        // Using the configuration which set by the component as a default one
        // Since the configuration's properties will be set by the URI
        // we need to copy or create a new MinaConfiguration here
        MinaConfiguration config;
        if (configuration != null) {
            config = configuration.copy();
        } else {
            config = new MinaConfiguration();
        }

        URI u = new URI(remaining);
        config.setHost(u.getHost());
        config.setPort(u.getPort());
        config.setProtocol(u.getScheme());
        config.setFilters(resolveAndRemoveReferenceListParameter(parameters, "filters", IoFilter.class));

        Endpoint endpoint = createEndpoint(uri, config);
        setProperties(endpoint, parameters);

        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    public Endpoint createEndpoint(MinaConfiguration config) throws Exception {
        return createEndpoint(config.getUriString(), config);
    }

    private Endpoint createEndpoint(String uri, MinaConfiguration config) throws Exception {
        ObjectHelper.notNull(getCamelContext(), "camelContext");
        String protocol = config.getProtocol();
        // if mistyped uri then protocol can be null

        MinaEndpoint endpoint = null;
        if (protocol != null) {
            if (protocol.equals("tcp") || config.isDatagramProtocol() || protocol.equals("vm")) {
                endpoint = new MinaEndpoint(uri, this, config);
            }
        }
        if (endpoint == null) {
            // protocol not resolved so error
            throw new IllegalArgumentException("Unrecognised MINA protocol: " + protocol + " for uri: " + uri);
        }

        // set sync or async mode after endpoint is created
        if (config.isSync()) {
            endpoint.setExchangePattern(ExchangePattern.InOut);
        } else {
            endpoint.setExchangePattern(ExchangePattern.InOnly);
        }

        return endpoint;
    }

    // Properties
    //-------------------------------------------------------------------------
    public MinaConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared mina configuration.
     */
    public void setConfiguration(MinaConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable usage of global SSL context parameters.
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }

}
