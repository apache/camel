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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudFunctionsConfiguration implements Cloneable {

    @UriPath(label = "common", description = "The user-defined name of the function")
    @Metadata(required = true)
    private String functionName;

    @UriParam(label = "common", description = "Service account key to authenticate an application as a service account")
    private String serviceAccountKey;

    @UriParam(label = "producer", description = "The Google Cloud Project name where the Function is located")
    private String project;

    @UriParam(label = "producer", description = "The Google Cloud Location (Region) where the Function is located")
    private String location;

    @UriParam(label = "producer",
              enums = "listFunctions,getFunction,callFunction,generateDownloadUrl,generateUploadUrl,createFunction,updateFunction,deleteFunction")
    private GoogleCloudFunctionsOperations operation;

    @UriParam(defaultValue = "false", description = "Specifies if the request is a pojo request")
    private boolean pojoRequest;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private CloudFunctionsServiceClient client;

    public String getFunctionName() {
        return functionName;
    }

    /**
     * Set the function name
     *
     * @param functionName
     */
    public void setFunctionName(String functionName) {
        this.functionName = functionName;
    }

    public String getServiceAccountKey() {
        return serviceAccountKey;
    }

    /**
     * Service account key
     *
     * @param serviceAccountKey
     */
    public void setServiceAccountKey(String serviceAccountKey) {
        this.serviceAccountKey = serviceAccountKey;
    }

    public String getProject() {
        return project;
    }

    /**
     * The project to work with.
     *
     * @param project
     */
    public void setProject(String project) {
        this.project = project;
    }

    public String getLocation() {
        return location;
    }

    /**
     * The target location.
     *
     * @param location
     */
    public void setLocation(String location) {
        this.location = location;
    }

    public GoogleCloudFunctionsOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform on the producer.
     */
    public void setOperation(GoogleCloudFunctionsOperations operation) {
        this.operation = operation;
    }

    public CloudFunctionsServiceClient getClient() {
        return client;
    }

    /**
     * The client to use during service invocation.
     */
    public void setClient(CloudFunctionsServiceClient client) {
        this.client = client;
    }

    public boolean isPojoRequest() {
        return pojoRequest;
    }

    /**
     * Configure the input type. If true the message will be POJO type.
     *
     * @param pojoRequest
     */
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
