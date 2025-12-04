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

package org.apache.camel.component.azure.eventgrid;

import com.azure.core.credential.AzureKeyCredential;
import com.azure.core.credential.TokenCredential;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class EventGridConfiguration implements Cloneable {

    @UriPath
    @Metadata(required = true)
    private String topicEndpoint;

    @UriParam(label = "security", secret = true)
    private String accessKey;

    @UriParam(label = "security", secret = true)
    @Metadata(autowired = true)
    private AzureKeyCredential azureKeyCredential;

    @UriParam(label = "security", secret = true)
    @Metadata(autowired = true)
    private TokenCredential tokenCredential;

    @UriParam(label = "security", enums = "ACCESS_KEY,AZURE_IDENTITY,TOKEN_CREDENTIAL", defaultValue = "ACCESS_KEY")
    private CredentialType credentialType = CredentialType.ACCESS_KEY;

    @UriParam(label = "producer")
    @Metadata(autowired = true)
    private EventGridPublisherClient<com.azure.core.models.CloudEvent> publisherClient;

    /**
     * The topic endpoint URL where events will be published.
     */
    public String getTopicEndpoint() {
        return topicEndpoint;
    }

    public void setTopicEndpoint(String topicEndpoint) {
        this.topicEndpoint = topicEndpoint;
    }

    /**
     * The access key for the Event Grid topic. Required when using ACCESS_KEY credential type.
     */
    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    /**
     * The Azure Key Credential for authentication. This is automatically created from the accessKey if not provided.
     */
    public AzureKeyCredential getAzureKeyCredential() {
        return azureKeyCredential;
    }

    public void setAzureKeyCredential(AzureKeyCredential azureKeyCredential) {
        this.azureKeyCredential = azureKeyCredential;
    }

    /**
     * Provide custom authentication credentials using an implementation of {@link TokenCredential}.
     */
    public TokenCredential getTokenCredential() {
        return tokenCredential;
    }

    public void setTokenCredential(TokenCredential tokenCredential) {
        this.tokenCredential = tokenCredential;
    }

    /**
     * Determines the credential strategy to adopt
     */
    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    /**
     * The EventGrid publisher client. If provided, it will be used instead of creating a new one.
     */
    public EventGridPublisherClient<com.azure.core.models.CloudEvent> getPublisherClient() {
        return publisherClient;
    }

    public void setPublisherClient(EventGridPublisherClient<com.azure.core.models.CloudEvent> publisherClient) {
        this.publisherClient = publisherClient;
    }

    public EventGridConfiguration copy() {
        try {
            return (EventGridConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
