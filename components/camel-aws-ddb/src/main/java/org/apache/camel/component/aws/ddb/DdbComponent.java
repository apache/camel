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
package org.apache.camel.component.aws.ddb;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-ddb")
public class DdbComponent extends DefaultComponent {

    @Metadata 
    private DdbConfiguration configuration = new DdbConfiguration();
    
    public DdbComponent() {
        this(null);
    }

    public DdbComponent(CamelContext context) {
        super(context);
        
        registerExtension(new DdbComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        if (remaining == null || remaining.trim().length() == 0) {
            throw new IllegalArgumentException("Table name must be specified.");
        }
        DdbConfiguration configuration = this.configuration != null ? this.configuration.copy() : new DdbConfiguration();
        configuration.setTableName(remaining);
        DdbEndpoint endpoint = new DdbEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonDDBClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonDDBClient or accessKey and secretKey must be specified");
        }

        return endpoint;
    }
    
    public DdbConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The component configuration
     */
    public void setConfiguration(DdbConfiguration configuration) {
        this.configuration = configuration;
    }
    
    private void checkAndSetRegistryClient(DdbConfiguration configuration) {
        Set<AmazonDynamoDB> clients = getCamelContext().getRegistry().findByType(AmazonDynamoDB.class);
        if (clients.size() == 1) {
            configuration.setAmazonDDBClient(clients.stream().findFirst().get());
        }
    }
}
