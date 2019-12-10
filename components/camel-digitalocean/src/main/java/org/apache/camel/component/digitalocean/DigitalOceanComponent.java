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
package org.apache.camel.component.digitalocean;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.component.digitalocean.constants.DigitalOceanResources;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("digitalocean")
public class DigitalOceanComponent extends DefaultComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(DigitalOceanComponent.class);

    public DigitalOceanComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        DigitalOceanConfiguration configuration = new DigitalOceanConfiguration();
        configuration.setResource(DigitalOceanResources.valueOf(remaining));

        DigitalOceanEndpoint endpoint = new DigitalOceanEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        if (ObjectHelper.isEmpty(configuration.getOAuthToken()) && ObjectHelper.isEmpty(configuration.getDigitalOceanClient())) {
            throw new DigitalOceanException("oAuthToken or digitalOceanClient must be specified");
        }

        return endpoint;
    }

}
