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
package org.apache.camel.component.aws.swf;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.UriEndpointComponent;
import org.apache.camel.util.IntrospectionSupport;

/**
 * Defines the <a href="http://aws.amazon.com/swf/">Amazon Simple Workflow Component</a>
 */
public class SWFComponent extends UriEndpointComponent {

    public SWFComponent() {
        super(SWFEndpoint.class);
    }

    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Map<String, Object> clientConfigurationParameters = IntrospectionSupport.extractProperties(parameters, "clientConfiguration.");
        Map<String, Object> sWClientParameters = IntrospectionSupport.extractProperties(parameters, "sWClient.");
        Map<String, Object> startWorkflowOptionsParameters = IntrospectionSupport.extractProperties(parameters, "startWorkflowOptions.");

        SWFConfiguration configuration = new SWFConfiguration();
        configuration.setType(remaining);
        setProperties(configuration, parameters);
        configuration.setClientConfigurationParameters(clientConfigurationParameters);
        configuration.setSWClientParameters(sWClientParameters);
        configuration.setStartWorkflowOptionsParameters(startWorkflowOptionsParameters);

        return new SWFEndpoint(uri, this, configuration);
    }
}
