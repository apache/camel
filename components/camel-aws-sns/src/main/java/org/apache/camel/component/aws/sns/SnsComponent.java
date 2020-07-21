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
package org.apache.camel.component.aws.sns;

import java.util.Map;
import java.util.Set;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.sns.AmazonSNS;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-sns")
public class SnsComponent extends DefaultComponent {
    
    @Metadata  
    private SnsConfiguration configuration = new SnsConfiguration();
    
    public SnsComponent() {
        this(null);
    }

    public SnsComponent(CamelContext context) {
        super(context);
        
        registerExtension(new SnsComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Topic name must be specified.");
        }
        SnsConfiguration configuration =  this.configuration != null ? this.configuration.copy() : new SnsConfiguration();
        if (remaining.startsWith("arn:")) {
            String[] parts = remaining.split(":");
            if (parts.length != 6 || !parts[2].equals("sns")) {
                throw new IllegalArgumentException("Topic arn must be in format arn:aws:sns:region:account:name.");
            }
            configuration.setTopicArn(remaining);
            configuration.setRegion(Regions.fromName(parts[3]).toString());
        } else {
            configuration.setTopicName(remaining);
        }
        SnsEndpoint endpoint = new SnsEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonSNSClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("AmazonSNSClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }
    
    public SnsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(SnsConfiguration configuration) {
        this.configuration = configuration;
    }
    
    private void checkAndSetRegistryClient(SnsConfiguration configuration) {
        Set<AmazonSNS> clients = getCamelContext().getRegistry().findByType(AmazonSNS.class);
        if (clients.size() == 1) {
            configuration.setAmazonSNSClient(clients.stream().findFirst().get());
        }
    }
}
