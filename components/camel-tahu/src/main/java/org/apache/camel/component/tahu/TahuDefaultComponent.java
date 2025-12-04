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

package org.apache.camel.component.tahu;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.camel.CamelContext;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;

public abstract class TahuDefaultComponent extends DefaultComponent implements SSLContextParametersAware {

    protected static final ConcurrentMap<String, TahuDefaultEndpoint> endpoints = new ConcurrentHashMap<>();

    @Metadata(label = "advanced")
    TahuConfiguration configuration;

    @Metadata(label = "security", defaultValue = "false")
    boolean useGlobalSslContextParameters;

    protected TahuDefaultComponent() {
        this.configuration = createConfiguration();
    }

    protected TahuDefaultComponent(CamelContext camelContext) {
        super(camelContext);
        this.configuration = createConfiguration();
    }

    protected TahuDefaultComponent(TahuConfiguration configuration) {
        this.configuration = configuration;
    }

    protected abstract TahuDefaultEndpoint doCreateEndpoint(
            String uri, List<String> descriptorSegments, TahuConfiguration tahuConfig) throws Exception;

    private List<String> getDescriptorSegments(String remaining) {
        return Arrays.stream(remaining.split(TahuConstants.MAJOR_SEPARATOR, 3))
                .map(String::trim)
                .filter(ObjectHelper::isNotEmpty)
                .toList();
    }

    @Override
    protected TahuDefaultEndpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
            throws Exception {

        if (ObjectHelper.isEmpty(remaining)) {
            throw new ResolveEndpointFailedException(uri, "empty remaining segments");
        }

        TahuDefaultEndpoint answer = endpoints.computeIfAbsent(remaining, r -> {
            List<String> descriptorSegments = getDescriptorSegments(remaining);

            // Each endpoint can have its own configuration so make a copy of the
            // configuration
            TahuConfiguration endpointConfig = getConfiguration().copy();

            if (endpointConfig.getSslContextParameters() == null) {
                endpointConfig.setSslContextParameters(retrieveGlobalSslContextParameters());
            }

            try {
                TahuDefaultEndpoint newEndpoint = doCreateEndpoint(uri, descriptorSegments, endpointConfig);

                setProperties(newEndpoint, parameters);

                return newEndpoint;
            } catch (Exception e) {
                throw new ResolveEndpointFailedException(uri, e);
            }
        });

        return answer;
    }

    public TahuConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared Tahu configuration
     */
    public void setConfiguration(TahuConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Factory method to create the default configuration instance
     *
     * @return a newly created configuration object which can then be further customized
     */
    TahuConfiguration createConfiguration() {
        return new TahuConfiguration();
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return this.useGlobalSslContextParameters;
    }

    /**
     * Enable/disable global SSL context parameters use
     */
    @Override
    public void setUseGlobalSslContextParameters(boolean useGlobalSslContextParameters) {
        this.useGlobalSslContextParameters = useGlobalSslContextParameters;
    }
}
