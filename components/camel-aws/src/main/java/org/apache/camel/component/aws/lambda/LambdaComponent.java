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
package org.apache.camel.component.aws.lambda;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;

public class LambdaComponent extends DefaultComponent {

    @Metadata
    private String accessKey;
    @Metadata
    private String secretKey;
    @Metadata
    private String region;
    @Metadata(label = "advanced")    
    private LambdaConfiguration configuration;
    
    public LambdaComponent() {
        this(null);
    }

    public LambdaComponent(CamelContext context) {
        super(context);
        
        this.configuration = new LambdaConfiguration();
        registerExtension(new LambdaComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        LambdaConfiguration configuration = this.configuration.copy();
        setProperties(configuration, parameters);
        configuration.setFunction(remaining);

        if (ObjectHelper.isEmpty(configuration.getAccessKey())) {
            setAccessKey(accessKey);
        }
        if (ObjectHelper.isEmpty(configuration.getSecretKey())) {
            setSecretKey(secretKey);
        }
        if (ObjectHelper.isEmpty(configuration.getRegion())) {
            setRegion(region);
        }
        if (ObjectHelper.isEmpty(configuration.getAwsLambdaClient()) && (ObjectHelper.isEmpty(configuration.getAccessKey()) || ObjectHelper.isEmpty(configuration.getSecretKey()))) {
            throw new IllegalArgumentException("accessKey/secretKey or awsLambdaClient must be specified");
        }

        LambdaEndpoint endpoint = new LambdaEndpoint(uri, this, configuration);
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

    public String getAccessKey() {
        return configuration.getAccessKey();
    }

    /**
     * Amazon AWS Access Key
     */
    public void setAccessKey(String accessKey) {
        configuration.setAccessKey(accessKey);
    }

    public String getSecretKey() {
        return configuration.getSecretKey();
    }

    /**
     * Amazon AWS Secret Key
     */
    public void setSecretKey(String secretKey) {
        configuration.setSecretKey(secretKey);
    }

    public String getRegion() {
        return configuration.getRegion();
    }

    /**
     * Amazon AWS Region
     */
    public void setRegion(String region) {
        configuration.setRegion(region);
    }
}
