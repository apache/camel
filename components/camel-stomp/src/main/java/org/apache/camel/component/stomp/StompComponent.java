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
package org.apache.camel.component.stomp;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import org.apache.camel.support.HeaderFilterStrategyComponent;

@Component("stomp")
public class StompComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    @Metadata(label = "advanced")
    private StompConfiguration configuration = new StompConfiguration();
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public StompComponent() {
    }
    
    // Implementation methods
    // -------------------------------------------------------------------------


    @Override
    protected void doInit() throws Exception {
        super.doInit();
        if (getHeaderFilterStrategy() == null) {
            setHeaderFilterStrategy(new DefaultHeaderFilterStrategy());
        }
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        // must copy config so we do not have side effects
        StompConfiguration config = getConfiguration().copy();

        StompEndpoint endpoint = new StompEndpoint(uri, this, config, remaining);
        
        // set header filter strategy and then call set properties 
        // if user wants to add CustomHeaderFilterStrategy
        endpoint.setHeaderFilterStrategy(getHeaderFilterStrategy());
        
        setProperties(endpoint, parameters);

        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return endpoint;
    }

    public StompConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration.
     */
    public void setConfiguration(StompConfiguration configuration) {
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
