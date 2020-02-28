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
package org.apache.camel.component.aws.eks;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.eks.AmazonEKS;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For working with Amazon EKS.
 */
@Component("aws-eks")
public class EKSComponent extends DefaultComponent {

    @Metadata 
    private EKSConfiguration configuration = new EKSConfiguration();
    
    public EKSComponent() {
        this(null);
    }
    
    public EKSComponent(CamelContext context) {
        super(context);
        
        registerExtension(new EKSComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        EKSConfiguration configuration =  this.configuration != null ? this.configuration.copy() : new EKSConfiguration();
        EKSEndpoint endpoint = new EKSEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getEksClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("Amazon eks client or accessKey and secretKey must be specified");
        }
        
        return endpoint;
    }
    
    public EKSConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(EKSConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(EKSConfiguration configuration) {
        Set<AmazonEKS> clients = getCamelContext().getRegistry().findByType(AmazonEKS.class);
        if (clients.size() == 1) {
            configuration.setEksClient(clients.stream().findFirst().get());
        }
    }
}
