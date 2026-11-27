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
import com.azure.core.models.CloudEvent;
import com.azure.messaging.eventgrid.EventGridPublisherClient;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.azure.common.CredentialType;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class EventGridConfiguration implements Cloneable {

    @UriPath(description = "The endpoint URL of the Event Grid topic that the producer publishes the events to, for example"
                           + " https://mytopic.westeurope-1.eventgrid.azure.net/api/events. The topic must accept the"
                           + " CloudEvents 1.0 schema. Not used when the publisherClient option is set.")
    @Metadata(required = true)
    private String topicEndpoint;

    @UriParam(label = "security", security = "secret",
              description = "The access key of the Event Grid topic, for credentialType ACCESS_KEY. The azureKeyCredential"
                            + " option replaces it, and it is not used when the tokenCredential option is set.")
    private String accessKey;

    @UriParam(label = "security", security = "secret",
              description = "The access key of the Event Grid topic as a com.azure.core.credential.AzureKeyCredential, for"
                            + " credentialType ACCESS_KEY. It replaces the accessKey option, and it is not used when the"
                            + " tokenCredential option is set.")
    @Metadata(autowired = true)
    private AzureKeyCredential azureKeyCredential;

    @UriParam(label = "security", security = "secret",
              description = "The com.azure.core.credential.TokenCredential that authenticates the producer with Microsoft"
                            + " Entra ID. When it is set, the accessKey and azureKeyCredential options are not used.")
    @Metadata(autowired = true)
    private TokenCredential tokenCredential;

    @UriParam(label = "security", enums = "ACCESS_KEY,AZURE_IDENTITY,TOKEN_CREDENTIAL", defaultValue = "ACCESS_KEY",
              description = "How the producer authenticates: ACCESS_KEY with the azureKeyCredential or accessKey option,"
                            + " TOKEN_CREDENTIAL with the tokenCredential option, or AZURE_IDENTITY with a"
                            + " DefaultAzureCredential of the Azure Identity library. When one of these credential options is"
                            + " set, the component chooses the type from it, so set this option only for AZURE_IDENTITY.")
    private CredentialType credentialType = CredentialType.ACCESS_KEY;

    @UriParam(label = "producer",
              description = "To use a custom com.azure.messaging.eventgrid.EventGridPublisherClient for CloudEvents. When"
                            + " it is set or autowired, the topicEndpoint and the credential options are not used.")
    @Metadata(autowired = true)
    private EventGridPublisherClient<CloudEvent> publisherClient;

    public String getTopicEndpoint() {
        return topicEndpoint;
    }

    public void setTopicEndpoint(String topicEndpoint) {
        this.topicEndpoint = topicEndpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public AzureKeyCredential getAzureKeyCredential() {
        return azureKeyCredential;
    }

    public void setAzureKeyCredential(AzureKeyCredential azureKeyCredential) {
        this.azureKeyCredential = azureKeyCredential;
    }

    public TokenCredential getTokenCredential() {
        return tokenCredential;
    }

    public void setTokenCredential(TokenCredential tokenCredential) {
        this.tokenCredential = tokenCredential;
    }

    public CredentialType getCredentialType() {
        return credentialType;
    }

    public void setCredentialType(CredentialType credentialType) {
        this.credentialType = credentialType;
    }

    public EventGridPublisherClient<CloudEvent> getPublisherClient() {
        return publisherClient;
    }

    public void setPublisherClient(EventGridPublisherClient<CloudEvent> publisherClient) {
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
