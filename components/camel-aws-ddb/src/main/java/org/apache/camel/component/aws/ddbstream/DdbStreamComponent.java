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
package org.apache.camel.component.aws.ddbstream;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBStreams;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-ddbstream")
public class DdbStreamComponent extends DefaultComponent {
    
    @Metadata 
    private DdbStreamConfiguration configuration = new DdbStreamConfiguration();

    public DdbStreamComponent() {
        this(null);
    }

    public DdbStreamComponent(CamelContext context) {
        super(context);
        
        registerExtension(new DdbStreamComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        
        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Table name must be specified.");
        }
        DdbStreamConfiguration configuration = this.configuration != null ? this.configuration.copy() : new DdbStreamConfiguration();
        configuration.setTableName(remaining);
        DdbStreamEndpoint endpoint = new DdbStreamEndpoint(uri, configuration, this);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonDynamoDbStreamsClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonDDBStreamsClient or accessKey and secretKey must be specified");
        }
        return endpoint;
    }
    
    public DdbStreamConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(DdbStreamConfiguration configuration) {
        this.configuration = configuration;
    }
    
    private void checkAndSetRegistryClient(DdbStreamConfiguration configuration) {
        Set<AmazonDynamoDBStreams> clients = getCamelContext().getRegistry().findByType(AmazonDynamoDBStreams.class);
        if (clients.size() == 1) {
            configuration.setAmazonDynamoDbStreamsClient(clients.stream().findFirst().get());
        }
    }
}
