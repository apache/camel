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
package org.apache.camel.component.alibaba.fc;

import com.aliyun.fc_open20210406.Client;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.fc.constants.FCHeaders;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Invoke serverless functions on Alibaba Cloud Function Compute (FC).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-fc", title = "Alibaba Function Compute (FC)",
             syntax = "alibaba-fc:operation", category = { Category.CLOUD, Category.SERVERLESS },
             headersClass = FCHeaders.class, producerOnly = true)
public class FCEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to perform", displayName = "Operation", label = "producer",
             enums = "invokeFunction,getFunction")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Alibaba Cloud region", displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "FC endpoint URL. Carries higher precedence than region based client initialization",
              displayName = "Endpoint")
    private String endpoint;

    @UriParam(description = "Access key for the cloud user", displayName = "Access Key",
              secret = true, security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "Secret Key",
              secret = true, security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Keys",
              secret = true, security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "FC service name", displayName = "Service Name")
    private String serviceName;

    @UriParam(description = "FC function name", displayName = "Function Name")
    private String functionName;

    @UriParam(description = "Function version or alias qualifier", displayName = "Qualifier")
    private String qualifier;

    @UriParam(description = "Autowire an existing FC client instance", displayName = "FC Client", label = "advanced")
    @Metadata(autowired = true)
    private Client fcClient;

    private boolean autowiredFcClient;

    public FCEndpoint() {
    }

    public FCEndpoint(String uri, String operation, FCComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new FCProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public Client initClient() throws Exception {
        if (fcClient != null) {
            return fcClient;
        }
        fcClient = FCUtils.createClient(this);
        return fcClient;
    }

    @Override
    protected void doStop() throws Exception {
        if (fcClient != null && !autowiredFcClient) {
            fcClient = null;
        }
        super.doStop();
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(String serviceName) {
        this.serviceName = serviceName;
    }

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getQualifier() {
        return qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }

    public Client getFcClient() {
        return fcClient;
    }

    public void setFcClient(Client fcClient) {
        this.fcClient = fcClient;
        this.autowiredFcClient = fcClient != null;
    }
}
