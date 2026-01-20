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
package org.apache.camel.component.ibm.watsonx.ai;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Component for interacting with IBM watsonx.ai foundation models.
 */
@Component("ibm-watsonx-ai")
public class WatsonxAiComponent extends DefaultComponent {

    @Metadata
    private WatsonxAiConfiguration configuration = new WatsonxAiConfiguration();

    public WatsonxAiComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        WatsonxAiConfiguration configuration
                = this.configuration != null ? this.configuration.copy() : new WatsonxAiConfiguration();

        WatsonxAiEndpoint endpoint = new WatsonxAiEndpoint(uri, this, configuration);
        endpoint.setLabel(remaining);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public WatsonxAiConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(WatsonxAiConfiguration configuration) {
        this.configuration = configuration;
    }
}
