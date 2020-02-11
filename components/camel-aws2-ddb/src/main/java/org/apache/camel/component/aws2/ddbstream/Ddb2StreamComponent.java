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
package org.apache.camel.component.aws2.ddbstream;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

import software.amazon.awssdk.services.dynamodb.streams.DynamoDbStreamsClient;

@Component("aws2-ddbstream")
public class Ddb2StreamComponent extends DefaultComponent {
    
    @Metadata
    private String accessKey;
    @Metadata
    private String secretKey;
    @Metadata
    private String region;
    @Metadata(label = "advanced")    
    private Ddb2StreamConfiguration configuration;

    public Ddb2StreamComponent() {
        this(null);
    }

    public Ddb2StreamComponent(CamelContext context) {
        super(context);
        
        registerExtension(new Ddb2StreamComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Table name must be specified.");
        }
        Ddb2StreamConfiguration configuration = this.configuration != null ? this.configuration.copy() : new Ddb2StreamConfiguration();
        configuration.setTableName(remaining);
        Ddb2StreamEndpoint endpoint = new Ddb2StreamEndpoint(uri, configuration, this);
        endpoint.getConfiguration().setAccessKey(accessKey);
        endpoint.getConfiguration().setSecretKey(secretKey);
        endpoint.getConfiguration().setRegion(region);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonDynamoDbStreamsClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonDDBStreamsClient or accessKey and secretKey must be specified");
        }
        return endpoint;
    }
    
    public Ddb2StreamConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The AWS DDB stream default configuration
     */
    public void setConfiguration(Ddb2StreamConfiguration configuration) {
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
     * Amazon AWS Region
     */
    public void setRegion(String region) {
        this.region = region;
    }
    
    private void checkAndSetRegistryClient(Ddb2StreamConfiguration configuration) {
        Set<DynamoDbStreamsClient> clients = getCamelContext().getRegistry().findByType(DynamoDbStreamsClient.class);
        if (clients.size() == 1) {
            configuration.setAmazonDynamoDbStreamsClient(clients.stream().findFirst().get());
        }
    }
}
