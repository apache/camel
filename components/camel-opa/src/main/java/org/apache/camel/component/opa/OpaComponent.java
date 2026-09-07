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
package org.apache.camel.component.opa;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;

/**
 * Open Policy Agent component.
 */
@Component("opa")
public class OpaComponent extends DefaultComponent {

    @Metadata
    private OpaConfiguration configuration = new OpaConfiguration();

    public OpaComponent() {
    }

    public OpaComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        if (ObjectHelper.isEmpty(remaining)) {
            throw new IllegalArgumentException("A policy path must be given, for example opa:authz/orders/allow");
        }

        final OpaConfiguration epConfiguration
                = this.configuration != null ? this.configuration.copy() : new OpaConfiguration();

        final OpaEndpoint endpoint = new OpaEndpoint(uri, this, epConfiguration);
        // opa:/authz/allow and opa:authz/allow name the same rule head; OPA paths are relative to /v1/data
        endpoint.setPolicyPath(StringHelper.removeStartingCharacters(remaining, '/'));
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * The component configuration.
     */
    public OpaConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(OpaConfiguration configuration) {
        this.configuration = configuration;
    }
}
