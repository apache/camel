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
package org.apache.camel.component.aws.mq;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.mq.AmazonMQ;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * For working with Amazon MQ.
 */
@Component("aws-mq")
public class MQComponent extends DefaultComponent {

    @Metadata  
    private MQConfiguration configuration = new MQConfiguration();
    
    public MQComponent() {
        this(null);
    }
    
    public MQComponent(CamelContext context) {
        super(context);
        
        registerExtension(new MQComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MQConfiguration configuration = this.configuration != null ? this.configuration.copy() : new MQConfiguration();
        MQEndpoint endpoint = new MQEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonMqClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonMQClient or accessKey and secretKey must be specified");
        }
        
        return endpoint;
    }
    
    public MQConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The Component configuration
     */
    public void setConfiguration(MQConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(MQConfiguration configuration) {
        Set<AmazonMQ> clients = getCamelContext().getRegistry().findByType(AmazonMQ.class);
        if (clients.size() == 1) {
            configuration.setAmazonMqClient(clients.stream().findFirst().get());
        }
    }
}
