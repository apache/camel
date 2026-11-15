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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "operation", value = "invokeFunction",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                    Carrier.NULL }),
                                  description = "invokeFunction sends the body as the payload of the function: text in UTF-8,"
                                                + " bytes as they are, a stream or a file as its bytes, so binary data is kept,"
                                                + " and no body as an empty payload. A Map or a POJO fails, as it does not"
                                                + " convert to bytes, so marshal it first. getFunction does not use the body"),
           producerOut = @BodyType(value = Carrier.MAP, javaType = "java.util.Map",
                                   description = "A Map: for invokeFunction the statusCode, the headers and the body of the"
                                                 + " response, which holds the payload the function returns as bytes, with"
                                                 + " the status code in the CamelAlibabaFcStatusCode header too; for"
                                                 + " getFunction the fields of the function, such as functionName, runtime"
                                                 + " and handler"))
public class FCEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs. The CamelAlibabaFcOperation header replaces this option"
                           + " for a request that the producer sends. If that header is not set, an exchange property of the"
                           + " same name replaces this option.",
             displayName = "Operation", label = "producer", enums = "invokeFunction,getFunction")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The region ID of Function Compute, for example cn-hangzhou. Only used when the endpoint option is"
                            + " not set, to build the endpoint of Function Compute. Not used when the fcClient option is set or"
                            + " autowired.",
              displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The endpoint of Function Compute, which is a host name without http:// or https://. The producer"
                            + " sends requests to it over HTTPS. If not set, the region option determines the endpoint, and one"
                            + " of the two is required. Not used when the fcClient option is set or autowired.",
              displayName = "Endpoint")
    private String endpoint;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for the requests that the producer sends to Function"
                            + " Compute. If not set, the accessKey of the serviceKeys option is used, and one of the two is"
                            + " required. Not used when the fcClient option is set or autowired.",
              displayName = "Access Key", security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for the requests that the producer sends to Function"
                            + " Compute. If not set, the secretKey of the serviceKeys option is used, and one of the two is"
                            + " required. Not used when the fcClient option is set or autowired.",
              displayName = "Secret Key", security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys. Its"
                            + " accessKey is the AccessKey ID and its secretKey is the AccessKey secret. Each of the two is"
                            + " used when the option of the same name is not set. Not used when the fcClient option is set or"
                            + " autowired.",
              displayName = "Service Keys", security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "The name of the service that contains the function. The CamelAlibabaFcServiceName header replaces"
                            + " this option for a request that the producer sends. If that header is not set, an exchange"
                            + " property of the same name replaces this option. One of the three is required.",
              displayName = "Service Name")
    private String serviceName;

    @UriParam(description = "The name of the function. The CamelAlibabaFcFunctionName header replaces this option for a request"
                            + " that the producer sends. If that header is not set, an exchange property of the same name"
                            + " replaces this option. One of the three is required.",
              displayName = "Function Name")
    private String functionName;

    @UriParam(description = "The version or alias of the service. The CamelAlibabaFcQualifier header replaces this option for a"
                            + " request that the producer sends. If that header is not set, an exchange property of the same"
                            + " name replaces this option. If none of the three is set, the producer adds no version or alias"
                            + " to the request.",
              displayName = "Qualifier")
    private String qualifier;

    @UriParam(description = "To use a custom com.aliyun.fc_open20210406.Client. The producer sends its requests with this"
                            + " client, and the region, endpoint, accessKey, secretKey and serviceKeys options are not used. If"
                            + " no client is set or autowired, the Camel endpoint creates a client from these options. It"
                            + " creates this client when the producer processes its first message.",
              displayName = "FC Client", label = "advanced")
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
