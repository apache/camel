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
package org.apache.camel.component.nagios;

import java.net.URI;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

/**
 * @version 
 */
public class NagiosComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private NagiosConfiguration configuration;

    public NagiosComponent() {
        configuration = new NagiosConfiguration();
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        URI url = new URI(uri);

        // must use copy as each endpoint can have different options
        ObjectHelper.notNull(configuration, "configuration");
        NagiosConfiguration config = configuration.copy();
        config.configure(url);
        setProperties(config, parameters);

        NagiosEndpoint endpoint = new NagiosEndpoint(uri, this);
        endpoint.setConfiguration(config);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public NagiosConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use a shared {@link NagiosConfiguration}
     */
    public void setConfiguration(NagiosConfiguration configuration) {
        this.configuration = configuration;
    }
}
