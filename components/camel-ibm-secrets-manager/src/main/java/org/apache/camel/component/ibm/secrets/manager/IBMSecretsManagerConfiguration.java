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
package org.apache.camel.component.ibm.secrets.manager;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class IBMSecretsManagerConfiguration implements Cloneable {

    @UriPath(description = "Logical name")
    @Metadata(required = true)
    private String label;
    @UriParam
    private String serviceUrl;
    @UriParam(label = "security", secret = true)
    private String token;
    @UriParam(label = "producer")
    private IBMSecretsManagerOperation operation = IBMSecretsManagerOperation.createArbitrarySecret;

    /**
     * Service URL for IBM Secrets Manager
     */
    public String getServiceUrl() {
        return serviceUrl;
    }

    public void setServiceUrl(String serviceUrl) {
        this.serviceUrl = serviceUrl;
    }

    /**
     * IBM Cloud API Token for IBM Secrets Manager
     */
    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    /**
     * Operation to be performed
     */
    public IBMSecretsManagerOperation getOperation() {
        return operation;
    }

    public void setOperation(IBMSecretsManagerOperation operation) {
        this.operation = operation;
    }

    // *************************************************
    //
    // *************************************************

    public IBMSecretsManagerConfiguration copy() {
        try {
            return (IBMSecretsManagerConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
