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
package org.apache.camel.component.google.secret.manager;

import com.google.cloud.secretmanager.v1.SecretManagerServiceClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleSecretManagerConfiguration implements Cloneable {

    @UriPath(label = "common", description = "The Google Cloud Project Id name related to the Secret Manager")
    @Metadata(required = true)
    private String project;

    @UriParam(label = "common", description = "Service account key to authenticate an application as a service account")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              enums = "createSecret")
    private GoogleSecretManagerOperations operation;

    @UriParam(defaultValue = "false", description = "Specifies if the request is a pojo request")
    private boolean pojoRequest;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private SecretManagerServiceClient client;

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

    public GoogleSecretManagerOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform on the producer.
     */
    public void setOperation(GoogleSecretManagerOperations operation) {
        this.operation = operation;
    }

    public SecretManagerServiceClient getClient() {
        return client;
    }

    /**
     * The client to use during service invocation.
     */
    public void setClient(SecretManagerServiceClient client) {
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

    public GoogleSecretManagerConfiguration copy() {
        try {
            return (GoogleSecretManagerConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

}
