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
package org.apache.camel.component.smpp;

import java.net.URI;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;

/**
 * @version 
 */
public class SmppComponent extends UriEndpointComponent {

    @Metadata(label = "advanced")
    private SmppConfiguration configuration;

    public SmppComponent() {
        super(SmppEndpoint.class);
    }

    public SmppComponent(SmppConfiguration configuration) {
        this();
        this.configuration = configuration;
    }

    public SmppComponent(CamelContext context) {
        super(context, SmppEndpoint.class);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        if (this.configuration == null) {
            this.configuration = new SmppConfiguration();
        }

        // create a copy of the configuration as other endpoints can adjust their copy as well
        SmppConfiguration config = this.configuration.copy();

        config.configureFromURI(new URI(uri));
        // TODO Camel 3.0 cmueller: We should change the default in Camel 3.0 to '' so that we can remove this special handling
        // special handling to set the system type to an empty string
        if (parameters.containsKey("systemType") && parameters.get("systemType") == null) {
            config.setSystemType("");
            parameters.remove("systemType");
        }
        // special handling to set the service type to an empty string
        if (parameters.containsKey("serviceType") && parameters.get("serviceType") == null) {
            config.setServiceType("");
            parameters.remove("serviceType");
        }
        setProperties(config, parameters);

        return createEndpoint(uri, config);
    }

    /**
     * Create a new smpp endpoint with the provided smpp configuration
     */
    protected Endpoint createEndpoint(SmppConfiguration config) throws Exception {
        return createEndpoint(null, config);
    }

    /**
     * Create a new smpp endpoint with the provided uri and smpp configuration
     */
    protected Endpoint createEndpoint(String uri, SmppConfiguration config) throws Exception {
        return new SmppEndpoint(uri, this, config);
    }

    public SmppConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared SmppConfiguration as configuration.
     */
    public void setConfiguration(SmppConfiguration configuration) {
        this.configuration = configuration;
    }
}