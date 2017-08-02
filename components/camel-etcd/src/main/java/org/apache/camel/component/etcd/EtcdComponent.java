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
package org.apache.camel.component.etcd;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.StringHelper;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Represents the component that manages {@link AbstractEtcdEndpoint}.
 */
public class EtcdComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced")
    private EtcdConfiguration configuration = new EtcdConfiguration();
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public EtcdComponent() {
        super();
    }

    public EtcdComponent(CamelContext context) {
        super(context);
    }

    // ************************************
    // Options
    // ************************************

    public String getUris() {
        return configuration.getUris();
    }

    /**
     * To set the URIs the client connects.
     * @param uris
     */
    public void setUris(String uris) {
        configuration.setUris(uris);
    }

    public SSLContextParameters getSslContextParameters() {
        return configuration.getSslContextParameters();
    }

    /**
     * To configure security using SSLContextParameters.
     * @param sslContextParameters
     */
    public void setSslContextParameters(SSLContextParameters sslContextParameters) {
        configuration.setSslContextParameters(sslContextParameters);
    }

    public String getUserName() {
        return configuration.getUserName();
    }

    /**
     * The user name to use for basic authentication.
     * @param userName
     */
    public void setUserName(String userName) {
        configuration.setUserName(userName);
    }

    public String getPassword() {
        return configuration.getPassword();
    }

    /**
     * The password to use for basic authentication.
     * @param password
     */
    public void setPassword(String password) {
        configuration.setPassword(password);
    }

    public EtcdConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Sets the common configuration shared among endpoints
     */
    public void setConfiguration(EtcdConfiguration configuration) {
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

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String ns = StringHelper.before(remaining, "/");
        String path = StringHelper.after(remaining, "/");

        if (ns == null) {
            ns = remaining;
        }
        if (path == null) {
            path = remaining;
        }

        EtcdNamespace namespace = getCamelContext().getTypeConverter().mandatoryConvertTo(EtcdNamespace.class, ns);
        EtcdConfiguration configuration = loadConfiguration(parameters);

        if (namespace != null) {
            // path must start with leading slash
            if (!path.startsWith("/")) {
                path = "/" + path;
            }

            switch (namespace) {
            case stats:
                return new EtcdStatsEndpoint(uri, this, configuration, namespace, path);
            case watch:
                return new EtcdWatchEndpoint(uri, this, configuration, namespace, path);
            case keys:
                return new EtcdKeysEndpoint(uri, this, configuration, namespace, path);
            default:
                throw new IllegalStateException("No endpoint for " + remaining);
            }
        }

        throw new IllegalStateException("No endpoint for " + remaining);
    }

    protected EtcdConfiguration loadConfiguration(Map<String, Object> parameters) throws Exception {
        EtcdConfiguration configuration = Optional.ofNullable(this.configuration).orElseGet(EtcdConfiguration::new).copy();
        configuration.setCamelContext(getCamelContext());

        setProperties(configuration, parameters);

        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return configuration;
    }
}
