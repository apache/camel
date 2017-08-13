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
package org.apache.camel.component.digitalocean;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanResources;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents the component that manages {@link DigitalOceanEndpoint}.
 */
public class DigitalOceanComponent extends UriEndpointComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(DigitalOceanComponent.class);


    public DigitalOceanComponent() {
        super(DigitalOceanEndpoint.class);
    }

    public DigitalOceanComponent(CamelContext context) {
        super(context, DigitalOceanEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        DigitalOceanConfiguration configuration = new DigitalOceanConfiguration();
        setProperties(configuration, parameters);
        configuration.setResource(DigitalOceanResources.valueOf(remaining));

        if (ObjectHelper.isEmpty(configuration.getOAuthToken()) && ObjectHelper.isEmpty(configuration.getDigitalOceanClient())) {
            throw new DigitalOceanException("oAuthToken or digitalOceanClient must be specified");
        }

        return new DigitalOceanEndpoint(uri, this, configuration);
    }

}
