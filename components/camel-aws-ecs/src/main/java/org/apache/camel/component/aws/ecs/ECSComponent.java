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
package org.apache.camel.component.aws.ecs;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.ecs.AmazonECS;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For working with Amazon ECS.
 */
@Component("aws-ecs")
public class ECSComponent extends DefaultComponent {

    @Metadata
    private String accessKey;
    @Metadata
    private String secretKey;
    @Metadata
    private String region;
    @Metadata(label = "advanced")    
    private ECSConfiguration configuration;
    
    public ECSComponent() {
        this(null);
    }
    
    public ECSComponent(CamelContext context) {
        super(context);
        
        registerExtension(new ECSComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ECSConfiguration configuration = this.configuration != null ? this.configuration.copy() : new ECSConfiguration();
        ECSEndpoint endpoint = new ECSEndpoint(uri, this, configuration);
        endpoint.getConfiguration().setAccessKey(accessKey);
        endpoint.getConfiguration().setSecretKey(secretKey);
        endpoint.getConfiguration().setRegion(region);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getEcsClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon ecs client or accessKey and secretKey must be specified");
        }
      
        return endpoint;
    }
    
    public ECSConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The AWS ECS default configuration
     */
    public void setConfiguration(ECSConfiguration configuration) {
        this.configuration = configuration;
    }

    public String getAccessKey() {
        return accessKey;
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }
    
    public String getRegion() {
        return region;
    }

    /**
     * The region in which ECS client needs to work
     */
    public void setRegion(String region) {
        this.region = region;
    }

    private void checkAndSetRegistryClient(ECSConfiguration configuration) {
        Set<AmazonECS> clients = getCamelContext().getRegistry().findByType(AmazonECS.class);
        if (clients.size() == 1) {
            configuration.setEcsClient(clients.stream().findFirst().get());
        }
    }
}
