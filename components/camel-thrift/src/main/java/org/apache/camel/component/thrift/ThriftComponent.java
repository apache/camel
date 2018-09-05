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
package org.apache.camel.component.thrift;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.jsse.SSLContextParameters;

/**
 * Represents the component that manages {@link ThriftEndpoint}.
 */
public class ThriftComponent extends DefaultComponent implements SSLContextParametersAware {
    
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ThriftConfiguration config = new ThriftConfiguration();

        config = parseConfiguration(config, uri, parameters);
        SSLContextParameters sslParameters = config.getSslParameters();
        if (config.getNegotiationType() == ThriftNegotiationType.SSL && sslParameters == null) {
            sslParameters = retrieveGlobalSslContextParameters();
            config.setSslParameters(sslParameters);
        }
        
        setProperties(config, parameters);

        Endpoint endpoint = new ThriftEndpoint(uri, this, config);
        return endpoint;
    }

    /**
     * Parses the configuration
     * 
     * @return the parsed and valid configuration to use
     */
    protected ThriftConfiguration parseConfiguration(ThriftConfiguration configuration, String remaining, Map<String, Object> parameters) throws Exception {
        configuration.parseURI(new URI(remaining), parameters, this);
        return configuration;
    }

    /**
     * Determine if the thrift component is using global SSL context parameters
     */
    @Override
    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
    }

    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }
}
