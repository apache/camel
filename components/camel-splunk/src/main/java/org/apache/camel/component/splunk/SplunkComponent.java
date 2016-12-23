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
package org.apache.camel.component.splunk;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.spi.Metadata;

/**
 * Represents the component that manages {@link SplunkEndpoint}.
 */
public class SplunkComponent extends UriEndpointComponent {

    @Metadata(label = "advanced")
    private SplunkConfigurationFactory splunkConfigurationFactory = new DefaultSplunkConfigurationFactory();

    public SplunkComponent() {
        super(SplunkEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SplunkConfiguration configuration = splunkConfigurationFactory.parseMap(parameters);
        configuration.setName(remaining);
        setProperties(configuration, parameters);
        return new SplunkEndpoint(uri, this, configuration);
    }

    public SplunkConfigurationFactory getSplunkConfigurationFactory() {
        return splunkConfigurationFactory;
    }

    /**
     * To use the {@link SplunkConfigurationFactory}
     */
    public void setSplunkConfigurationFactory(SplunkConfigurationFactory splunkConfigurationFactory) {
        this.splunkConfigurationFactory = splunkConfigurationFactory;
    }

}
