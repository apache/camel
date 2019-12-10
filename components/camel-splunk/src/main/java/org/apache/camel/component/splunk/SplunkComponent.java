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
package org.apache.camel.component.splunk;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link SplunkEndpoint}.
 */
@Component("splunk")
public class SplunkComponent extends DefaultComponent {

    @Metadata(label = "advanced")
    private SplunkConfigurationFactory splunkConfigurationFactory = new DefaultSplunkConfigurationFactory();

    public SplunkComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        SplunkConfiguration configuration = splunkConfigurationFactory.parseMap(parameters);
        SplunkEndpoint answer = new SplunkEndpoint(uri, this, configuration);
        setProperties(answer, parameters);
        configuration.setName(remaining);
        return answer;
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
