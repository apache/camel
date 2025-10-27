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
package org.apache.camel.component.ibm.watson.discovery;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class WatsonDiscoveryConfiguration implements Cloneable {

    @UriParam(label = "security", secret = true)
    @Metadata(required = true)
    private String apiKey;

    @UriParam(label = "common")
    private String serviceUrl;

    @UriParam(label = "common")
    @Metadata(required = true)
    private String projectId;

    @UriParam(label = "common")
    private String version = "2023-03-31";

    @UriParam(label = "producer")
    private WatsonDiscoveryOperations operation;

    @UriParam(label = "producer")
    private String collectionId;

    public String getApiKey() {
        return apiKey;
    }

    /**
     * The IBM Cloud API key for authentication
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getServiceUrl() {
        return serviceUrl;
    }

    /**
     * The service endpoint URL. If not specified, the default URL will be used.
     */
    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    public String getProjectId() {
        return projectId;
    }

    /**
     * The Watson Discovery project ID
     */
    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getVersion() {
        return version;
    }

    /**
     * The API version date (format: YYYY-MM-DD)
     */
    public void setVersion(String version) {
        this.version = version;
    }

    public WatsonDiscoveryOperations getOperation() {
        return operation;
    }

    /**
     * The operation to perform
     */
    public void setOperation(WatsonDiscoveryOperations operation) {
        this.operation = operation;
    }

    public String getCollectionId() {
        return collectionId;
    }

    /**
     * The collection ID for operations that require it
     */
    public void setCollectionId(String collectionId) {
        this.collectionId = collectionId;
    }

    public WatsonDiscoveryConfiguration copy() {
        try {
            return (WatsonDiscoveryConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
