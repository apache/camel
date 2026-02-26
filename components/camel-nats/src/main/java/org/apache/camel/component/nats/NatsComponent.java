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
package org.apache.camel.component.nats;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HeaderFilterStrategyComponent;

@Component("nats")
public class NatsComponent extends HeaderFilterStrategyComponent implements SSLContextParametersAware {

    @Metadata
    private NatsConfiguration configuration = new NatsConfiguration();

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NatsConfiguration config = configuration.copy();
        if (getHeaderFilterStrategy() != null) {
            config.setHeaderFilterStrategy(getHeaderFilterStrategy());
        }
        config.setTopic(remaining);

        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        NatsEndpoint answer = new NatsEndpoint(uri, this, config);
        setProperties(answer, parameters);
        return answer;
    }

    /**
     * @deprecated use getConfiguration()
     */
    @Deprecated
    public String getServers() {
        return configuration.getServers();
    }

    /**
     * @deprecated use getConfiguration()
     */
    @Deprecated
    public void setServers(String servers) {
        configuration.setServers(servers);
    }

    /**
     * @deprecated use getConfiguration()
     */
    @Deprecated
    public void setVerbose(boolean verbose) {
        configuration.setVerbose(verbose);
    }

    /**
     * @deprecated use getConfiguration()
     */
    @Deprecated
    public boolean isVerbose() {
        return configuration.isVerbose();
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

    public NatsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared configuration
     */
    public void setConfiguration(NatsConfiguration configuration) {
        this.configuration = configuration;
    }
}
