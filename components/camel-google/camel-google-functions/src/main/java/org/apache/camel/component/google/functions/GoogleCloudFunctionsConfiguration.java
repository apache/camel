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
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.common.GoogleCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudFunctionsConfiguration implements Cloneable, GoogleCommonConfiguration {

    @UriPath(label = "common",
             description = "The name of the function, without the projects/{project}/locations/{location}/functions/ prefix."
                           + " The producer builds the full name from it and the project and location options. Not used by"
                           + " listFunctions and generateUploadUrl, and not used when pojoRequest=true.")
    @Metadata(required = true)
    private String functionName;

    @UriParam(label = "common",
              description = "The service account key file (JSON) used to create the CloudFunctionsServiceClient. It is loaded as"
                            + " a resource with the classpath:, file:, http: or https: prefix, and a value without a prefix is"
                            + " read from the classpath. When not set, the Application Default Credentials are used, for example"
                            + " from the GOOGLE_APPLICATION_CREDENTIALS environment variable. Not used when the client option is"
                            + " set.")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              description = "The ID of the Google Cloud project that holds the functions. Every operation needs it, unless"
                            + " pojoRequest=true, where the request object names the project.")
    private String project;

    @UriParam(label = "producer",
              description = "The region of the functions, for example us-central1. Every operation needs it, unless"
                            + " pojoRequest=true, where the request object names the location.")
    private String location;

    @UriParam(label = "producer",
              description = "The operation that the producer performs with the Cloud Functions v1 API. When not set,"
                            + " callFunction is used, which Google allows only for limited traffic, such as tests. The"
                            + " CamelGoogleCloudFunctionsOperation header overrides this option. updateFunction works only"
                            + " with pojoRequest=true.",
              enums = "listFunctions,getFunction,callFunction,generateDownloadUrl,generateUploadUrl,createFunction,updateFunction,deleteFunction")
    private GoogleCloudFunctionsOperations operation;

    @UriParam(defaultValue = "false",
              description = "Whether the message body is the request object of the Google Cloud SDK for the operation, which"
                            + " is sent as it is: a ListFunctionsRequest, a CloudFunctionName for getFunction, a"
                            + " CallFunctionRequest, a GenerateDownloadUrlRequest, a GenerateUploadUrlRequest, a"
                            + " CreateFunctionRequest, an UpdateFunctionRequest or a DeleteFunctionRequest. The project,"
                            + " location and functionName options and the createFunction headers are then not used.")
    private boolean pojoRequest;

    @UriParam(label = "advanced",
              description = "The CloudFunctionsServiceClient to use for the calls. When not set, a single"
                            + " CloudFunctionsServiceClient in the registry is used, otherwise the endpoint creates one with"
                            + " the serviceAccountKey or the Application Default Credentials and closes it when it stops. A"
                            + " client that is set or autowired is not closed by the endpoint.")
    @Metadata(autowired = true)
    private CloudFunctionsServiceClient client;

    public String getFunctionName() {
        return functionName;
    }

    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    @Override
    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public GoogleCloudFunctionsOperations getOperation() {
        return operation;
    }

    public void setOperation(GoogleCloudFunctionsOperations operation) {
        this.operation = operation;
    }

    public CloudFunctionsServiceClient getClient() {
        return client;
    }

    public void setClient(CloudFunctionsServiceClient client) {
        this.client = client;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    public void setPojoRequest(boolean pojoRequest) {
        this.pojoRequest = pojoRequest;
    }

    public GoogleCloudFunctionsConfiguration copy() {
        try {
            return (GoogleCloudFunctionsConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
