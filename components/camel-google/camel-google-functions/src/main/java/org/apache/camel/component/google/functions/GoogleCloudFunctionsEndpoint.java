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
package org.apache.camel.component.google.functions;

import com.google.cloud.functions.v1.CloudFunctionsServiceClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.util.ObjectHelper;

/**
 * Manage and invoke Google Cloud Functions
 *
 * Google Functions Endpoint definition represents a function within the GCP and contains configuration to customize the
 * behavior of Producer.
 */
@UriEndpoint(firstVersion = "3.9.0", scheme = "google-functions", title = "Google Cloud Functions",
             syntax = "google-functions:functionName", category = {
                     Category.CLOUD },
             producerOnly = true, headersClass = GoogleCloudFunctionsConstants.class)
@BodyTypes(producerIn = @BodyType(value = { Carrier.TEXT, Carrier.BYTES, Carrier.STREAM }, dependsOn = "operation",
                                  byOption = @BodyType.When(option = "pojoRequest", value = "true",
                                                            carriers = Carrier.POJO),
                                  description = "The data to call the function with, for callFunction (the default),"
                                                + " converted to text: a Map or a POJO is sent as its toString(), so marshal"
                                                + " it first, and no body fails. The other operations take their input from"
                                                + " options and headers. With pojoRequest=true the request object of the"
                                                + " operation, such as a CallFunctionRequest"),
           producerOut = @BodyType(value = Carrier.TEXT, javaType = "java.lang.String",
                                   byOption = {
                                           @BodyType.When(option = "operation", value = "listFunctions",
                                                          carriers = Carrier.LIST,
                                                          javaType = "java.util.List<com.google.cloud.functions.v1.CloudFunction>"),
                                           @BodyType.When(option = "operation", value = "getFunction",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.functions.v1.CloudFunction"),
                                           @BodyType.When(option = "operation", value = "createFunction",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.functions.v1.CloudFunction"),
                                           @BodyType.When(option = "operation", value = "updateFunction",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.cloud.functions.v1.CloudFunction"),
                                           @BodyType.When(option = "operation", value = "deleteFunction",
                                                          carriers = Carrier.POJO,
                                                          javaType = "com.google.protobuf.Empty"),
                                           @BodyType.When(option = "operation", value = "generateDownloadUrl",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String"),
                                           @BodyType.When(option = "operation", value = "generateUploadUrl",
                                                          carriers = Carrier.TEXT, javaType = "java.lang.String") },
                                   description = "The result of the function as text for callFunction, with the whole"
                                                 + " CallFunctionResponse, which has the error of a failed call, in the"
                                                 + " CamelGoogleCloudFunctionsResponseObject header. By the operation: a"
                                                 + " List of CloudFunction for listFunctions, the CloudFunction for"
                                                 + " getFunction, createFunction and updateFunction, Empty for"
                                                 + " deleteFunction and the signed URL as text for the URL operations"))
public class GoogleCloudFunctionsEndpoint extends DefaultEndpoint implements EndpointServiceLocation {

    @UriParam
    private GoogleCloudFunctionsConfiguration configuration;

    private CloudFunctionsServiceClient cloudFunctionsClient;

    public GoogleCloudFunctionsEndpoint(String uri, GoogleCloudFunctionsComponent component,
                                        GoogleCloudFunctionsConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    public Producer createProducer() throws Exception {
        return new GoogleCloudFunctionsProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException(
                "Cannot consume from the google-functions endpoint: " + getEndpointUri());
    }

    public GoogleCloudFunctionsConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Setup configuration
     */
    public void setConfiguration(GoogleCloudFunctionsConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        if (configuration.getClient() != null) {
            cloudFunctionsClient = configuration.getClient();
        } else {
            cloudFunctionsClient = GoogleCloudFunctionsClientFactory.create(this.getCamelContext(), configuration);
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        if (configuration.getClient() == null && cloudFunctionsClient != null) {
            cloudFunctionsClient.close();
        }
    }

    public CloudFunctionsServiceClient getClient() {
        return cloudFunctionsClient;
    }

    @Override
    public String getServiceUrl() {
        if (ObjectHelper.isNotEmpty(configuration.getFunctionName()) && ObjectHelper.isNotEmpty(
                ObjectHelper.isNotEmpty(configuration.getProject()) && ObjectHelper.isNotEmpty(configuration.getLocation()))) {
            return getServiceProtocol() + ":" + configuration.getProject() + ":" + configuration.getFunctionName() + ":"
                   + configuration.getLocation();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "functions";
    }
}
