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
package org.apache.camel.component.aws.swf;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.simpleworkflow.AmazonSimpleWorkflow;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.util.PropertiesHelper;

@Component("aws-swf")
public class SWFComponent extends DefaultComponent {

    @Metadata
    private SWFConfiguration configuration = new SWFConfiguration();
    
    public SWFComponent() {
        this(null);
    }

    public SWFComponent(CamelContext context) {
        super(context);

        registerExtension(new SwfComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        Map<String, Object> clientConfigurationParameters = PropertiesHelper.extractProperties(parameters, "clientConfiguration.");
        Map<String, Object> sWClientParameters = PropertiesHelper.extractProperties(parameters, "sWClient.");
        Map<String, Object> startWorkflowOptionsParameters = PropertiesHelper.extractProperties(parameters, "startWorkflowOptions.");

        SWFConfiguration configuration = this.configuration != null ? this.configuration.copy() : new SWFConfiguration();
        configuration.setType(remaining);
        configuration.setClientConfigurationParameters(clientConfigurationParameters);
        configuration.setSWClientParameters(sWClientParameters);
        configuration.setStartWorkflowOptionsParameters(startWorkflowOptionsParameters);
        
        SWFEndpoint endpoint = new SWFEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonSWClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSWClient or accessKey and secretKey must be specified.");
        }
        return new SWFEndpoint(uri, this, configuration);
    }
    
    public SWFConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */    
    public void setConfiguration(SWFConfiguration configuration) {
        this.configuration = configuration;
    }
    
    private void checkAndSetRegistryClient(SWFConfiguration configuration) {
        Set<AmazonSimpleWorkflow> clients = getCamelContext().getRegistry().findByType(AmazonSimpleWorkflow.class);
        if (clients.size() == 1) {
            configuration.setAmazonSWClient(clients.stream().findFirst().get());
        }
    }
}
