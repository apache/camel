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
package org.apache.camel.component.etcd;

import java.util.Map;
import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.SSLContextParametersAware;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;

public abstract class AbstractEtcdComponent extends DefaultComponent implements SSLContextParametersAware {

    @Metadata
    private EtcdConfiguration configuration = new EtcdConfiguration();
    @Metadata(label = "security", defaultValue = "false")
    private boolean useGlobalSslContextParameters;

    public AbstractEtcdComponent() {
    }

    public AbstractEtcdComponent(CamelContext context) {
        super(context);
    }

    // ************************************
    // Options
    // ************************************


    public EtcdConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Component configuration.
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

    protected EtcdConfiguration loadConfiguration(Map<String, Object> parameters) throws Exception {
        EtcdConfiguration configuration = Optional.ofNullable(this.configuration).orElseGet(EtcdConfiguration::new).copy();
        configuration.setCamelContext(getCamelContext());

        if (configuration.getSslContextParameters() == null) {
            configuration.setSslContextParameters(retrieveGlobalSslContextParameters());
        }

        return configuration;
    }
}
