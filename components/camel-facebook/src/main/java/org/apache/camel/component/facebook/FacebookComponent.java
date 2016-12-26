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
package org.apache.camel.component.facebook;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.facebook.config.FacebookConfiguration;
import org.apache.camel.component.facebook.config.FacebookEndpointConfiguration;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Represents the component that manages {@link FacebookEndpoint}.
 */
public class FacebookComponent extends UriEndpointComponent {

    @Metadata(label = "advanced")
    private FacebookConfiguration configuration;

    public FacebookComponent() {
        this(new FacebookConfiguration());
    }

    public FacebookComponent(FacebookConfiguration configuration) {
        this(null, configuration);
    }

    public FacebookComponent(CamelContext context) {
        this(context, new FacebookConfiguration());
    }

    public FacebookComponent(CamelContext context, FacebookConfiguration configuration) {
        super(context, FacebookEndpoint.class);
        this.configuration = configuration;
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        FacebookEndpointConfiguration config = copyComponentProperties();
        final FacebookEndpoint endpoint = new FacebookEndpoint(uri, this, remaining, config);

        // set endpoint property inBody so that it's available in initState()
        setProperties(endpoint, parameters);

        // configure endpoint properties
        endpoint.configureProperties(parameters);

        // validate parameters
        validateParameters(uri, parameters, null);

        return endpoint;
    }

    private FacebookEndpointConfiguration copyComponentProperties() throws Exception {
        Map<String, Object> componentProperties = new HashMap<String, Object>();
        IntrospectionSupport.getProperties(configuration, componentProperties, null, false);

        // create endpoint configuration with component properties
        FacebookEndpointConfiguration config = new FacebookEndpointConfiguration();
        IntrospectionSupport.setProperties(config, componentProperties);
        return config;
    }

    public FacebookConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * To use the shared configuration
     */
    public void setConfiguration(FacebookConfiguration configuration) {
        this.configuration = configuration;
    }

}
