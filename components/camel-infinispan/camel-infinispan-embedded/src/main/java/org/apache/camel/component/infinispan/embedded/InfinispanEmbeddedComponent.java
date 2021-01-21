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
package org.apache.camel.component.infinispan.embedded;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.component.infinispan.InfinispanComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;

@Component(InfinispanEmbeddedComponent.SCHEME)
public class InfinispanEmbeddedComponent extends InfinispanComponent {
    public static final String SCHEME = "infinispan-embedded";

    @Metadata(description = "Component configuration")
    private InfinispanEmbeddedConfiguration configuration = new InfinispanEmbeddedConfiguration();

    public InfinispanEmbeddedComponent() {
    }

    public InfinispanEmbeddedComponent(CamelContext camelContext) {
        super(camelContext);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        InfinispanEmbeddedConfiguration conf = configuration.clone();

        InfinispanEmbeddedEndpoint endpoint = new InfinispanEmbeddedEndpoint(uri, remaining, this, conf);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public void setConfiguration(InfinispanEmbeddedConfiguration configuration) {
        this.configuration = configuration;
    }

    public InfinispanEmbeddedConfiguration getConfiguration() {
        return configuration;
    }
}
