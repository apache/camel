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
package org.apache.camel.component.openfga;

import java.util.Arrays;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.HealthCheckComponent;
import org.apache.camel.util.ObjectHelper;

/**
 * OpenFGA fine-grained authorization component.
 */
@Component("openfga")
public class OpenFgaComponent extends HealthCheckComponent implements SSLContextParametersAware {

    @Metadata
    private OpenFgaConfiguration configuration = new OpenFgaConfiguration();

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public OpenFgaComponent() {
    }

    public OpenFgaComponent(final CamelContext context) {
        super(context);
    }

    @Override
    public boolean isUseGlobalSslContextParameters() {
        return useGlobalSslContextParameters;
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
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException(
                    "An operation must be given, for example openfga:check. One of "
                                               + Arrays.toString(OpenFgaOperation.values()));
        }

        final OpenFgaConfiguration epConfiguration
                = this.configuration != null ? this.configuration.copy() : new OpenFgaConfiguration();

        final OpenFgaEndpoint endpoint = new OpenFgaEndpoint(uri, this, epConfiguration);
        endpoint.setOperation(parseOperation(remaining));
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Resolves the operation, reporting the ones that exist rather than the enum's own message - which names the Java
     * type and leaves the reader to guess the spelling.
     */
    private static OpenFgaOperation parseOperation(String remaining) {
        try {
            return OpenFgaOperation.valueOf(remaining);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "Unknown operation '" + remaining + "'; expected one of "
                                               + Arrays.toString(OpenFgaOperation.values()),
                    e);
        }
    }

    /**
     * The component configuration.
     */
    public OpenFgaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OpenFgaConfiguration configuration) {
        this.configuration = configuration;
    }
}
