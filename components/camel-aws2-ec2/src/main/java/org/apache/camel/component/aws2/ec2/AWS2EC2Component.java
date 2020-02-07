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
package org.apache.camel.component.aws2.ec2;

import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import software.amazon.awssdk.services.ec2.Ec2Client;

/**
 * For working with Amazon's Elastic Compute Cloud (EC2) SDK v2.
 */
@Component("aws2-ec2")
public class AWS2EC2Component extends DefaultComponent {

    @Metadata
    private String accessKey;
    @Metadata
    private String secretKey;
    @Metadata
    private String region;
    @Metadata(label = "advanced")
    private AWS2EC2Configuration configuration;

    public AWS2EC2Component() {
        this(null);
    }

    public AWS2EC2Component(CamelContext context) {
        super(context);

        registerExtension(new AWS2EC2ComponentVerifierExtension());
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {

        AWS2EC2Configuration configuration = this.configuration != null ? this.configuration.copy() : new AWS2EC2Configuration();
        AWS2EC2Endpoint endpoint = new AWS2EC2Endpoint(uri, this, configuration);
        endpoint.getConfiguration().setAccessKey(accessKey);
        endpoint.getConfiguration().setSecretKey(secretKey);
        endpoint.getConfiguration().setRegion(region);
        setProperties(endpoint, parameters);
        checkAndSetRegistryClient(configuration);
        if (configuration.getAmazonEc2Client() == null && (configuration.getAccessKey() == null || configuration.getSecretKey() == null)) {
            throw new IllegalArgumentException("amazonEC2Client or accessKey and secretKey must be specified");
        }

        return endpoint;
    }

    public AWS2EC2Configuration getConfiguration() {
        return configuration;
    }

    /**
     * The AWS EC2 default configuration
     */
    public void setConfiguration(AWS2EC2Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * The region in which EC2 client needs to work
     */
    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
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

    private void checkAndSetRegistryClient(AWS2EC2Configuration configuration) {
        Set<Ec2Client> clients = getCamelContext().getRegistry().findByType(Ec2Client.class);
        if (clients.size() == 1) {
            configuration.setAmazonEc2Client(clients.stream().findFirst().get());
        }
    }
}
