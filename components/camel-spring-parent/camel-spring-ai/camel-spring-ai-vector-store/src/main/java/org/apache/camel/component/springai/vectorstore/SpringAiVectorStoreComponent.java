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

package org.apache.camel.component.springai.vectorstore;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component(SpringAiVectorStore.SCHEME)
public class SpringAiVectorStoreComponent extends DefaultComponent {

    @Metadata
    private SpringAiVectorStoreConfiguration configuration;

    public SpringAiVectorStoreComponent() {
        this(null);
    }

    public SpringAiVectorStoreComponent(CamelContext context) {
        super(context);

        this.configuration = new SpringAiVectorStoreConfiguration();
    }

    public SpringAiVectorStoreConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The configuration.
     */
    public void setConfiguration(SpringAiVectorStoreConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SpringAiVectorStoreConfiguration configuration = this.configuration.copy();

        SpringAiVectorStoreEndpoint endpoint = new SpringAiVectorStoreEndpoint(uri, this, remaining, configuration);
        setProperties(endpoint, parameters);

        return endpoint;
    }
}
