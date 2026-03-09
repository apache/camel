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
package org.apache.camel.component.google.vision;

import com.google.cloud.vision.v1.ImageAnnotatorClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.google.common.GoogleCommonConfiguration;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class GoogleCloudVisionConfiguration implements Cloneable, GoogleCommonConfiguration {

    @UriPath(label = "common", description = "The operation name")
    @Metadata(required = true)
    private String operation;

    @UriParam(label = "common", description = "Service account key to authenticate an application as a service account")
    private String serviceAccountKey;

    @UriParam(label = "producer",
              description = "The max number of results to return per feature type. Default is unset (API default).")
    private Integer maxResults;

    @UriParam(defaultValue = "false", description = "Specifies if the request is a pojo request")
    private boolean pojoRequest;

    @UriParam(label = "advanced")
    @Metadata(autowired = true)
    private ImageAnnotatorClient client;

    public String getOperation() {
        return operation;
    }

    /**
     * Set the operation name
     *
     * @param operation
     */
    public void setOperation(String operation) {
        this.operation = operation;
    }

    @Override
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

    public Integer getMaxResults() {
        return maxResults;
    }

    /**
     * Max results to return per feature type.
     *
     * @param maxResults
     */
    public void setMaxResults(Integer maxResults) {
        this.maxResults = maxResults;
    }

    public ImageAnnotatorClient getClient() {
        return client;
    }

    /**
     * The client to use during service invocation.
     *
     * @param client
     */
    public void setClient(ImageAnnotatorClient client) {
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

    public GoogleCloudVisionConfiguration copy() {
        try {
            return (GoogleCloudVisionConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
