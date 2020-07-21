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
package org.apache.camel.component.aws.lambda;

import java.util.Map;
import java.util.Set;

import com.amazonaws.services.lambda.AWSLambda;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

@Component("aws-lambda")
public class LambdaComponent extends DefaultComponent {

    @Metadata
    private LambdaConfiguration configuration = new LambdaConfiguration();
    
    public LambdaComponent() {
        this(null);
    }

    public LambdaComponent(CamelContext context) {
        super(context);
        
        registerExtension(new LambdaComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LambdaConfiguration configuration = this.configuration != null ? this.configuration.copy() : new LambdaConfiguration();
        LambdaEndpoint endpoint = new LambdaEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);
        endpoint.setFunction(remaining);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAwsLambdaClient() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("accessKey/secretKey or awsLambdaClient must be specified");
        }

        return endpoint;
    }
    
    public LambdaConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * The AWS Lambda default configuration
     */
    public void setConfiguration(LambdaConfiguration configuration) {
        this.configuration = configuration;
    }

    private void checkAndSetRegistryClient(LambdaConfiguration configuration) {
        Set<AWSLambda> clients = getCamelContext().getRegistry().findByType(AWSLambda.class);
        if (clients.size() == 1) {
            configuration.setAwsLambdaClient(clients.stream().findFirst().get());
        }
    }
}
