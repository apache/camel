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
package org.apache.camel.component.infinispan;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.infinispan.commons.api.BasicCacheContainer;

public class InfinispanComponent extends DefaultComponent {
    @Metadata(description = "Default configuration")
    private InfinispanConfiguration configuration;
    @Metadata(description = "Default Cache container")
    private BasicCacheContainer cacheContainer;

    public InfinispanComponent() {
    }

    public InfinispanComponent(CamelContext camelContext) {
        super(camelContext);
    }

    public InfinispanConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The default configuration shared among endpoints.
     */
    public void setConfiguration(InfinispanConfiguration configuration) {
        this.configuration = configuration;
    }

    public BasicCacheContainer getCacheContainer() {
        return cacheContainer;
    }

    /**
     * The default cache container.
     */
    public void setCacheContainer(BasicCacheContainer cacheContainer) {
        this.cacheContainer = cacheContainer;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        InfinispanConfiguration conf;
        if (configuration != null) {
            conf = configuration.copy();
        } else {
            conf = new InfinispanConfiguration();
        }

        conf.setCacheContainer(cacheContainer);

        setProperties(conf, parameters);

        return new InfinispanEndpoint(uri, remaining, this, conf);
    }
}
