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
package org.apache.camel.component.nats;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;

public class NatsComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        NatsConfiguration config = new NatsConfiguration();
        setProperties(config, parameters);
        config.setServers(remaining);

        if (config.getSslContextParameters() == null) {
            config.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        NatsEndpoint endpoint = new NatsEndpoint(uri, this, config);
        return endpoint;
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
