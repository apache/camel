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
package org.apache.camel.component.whatsapp;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class WhatsAppConfiguration {

    @UriParam(description = "The authorization access token taken from whatsapp-business dashboard.", label = "security",
              secret = true)
    @Metadata(required = true)
    private String authorizationToken;

    @UriPath(description = "The phone number ID taken from whatsapp-business dashboard.")
    @Metadata(required = true)
    private String phoneNumberId;

    @UriParam(label = "advanced",
              description = "Can be used to set an alternative base URI, e.g. when you want to test the component against a mock WhatsApp API")
    private String baseUri;

    @UriParam(description = "Facebook graph api version.", label = "advanced")
    private String apiVersion;

    @UriParam(description = "Webhook verify token", label = "advanced")
    private String webhookVerifyToken;

    @UriParam(description = "Webhook path", label = "advanced", defaultValue = "webhook")
    private String webhookPath = "camel-whatsapp/webhook";

    public WhatsAppConfiguration() {
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public void setAuthorizationToken(String authorizationToken) {
        this.authorizationToken = authorizationToken;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public void setPhoneNumberId(String phoneNumberId) {
        this.phoneNumberId = phoneNumberId;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public String getWebhookVerifyToken() {
        return webhookVerifyToken;
    }

    public void setWebhookVerifyToken(String webhookVerifyToken) {
        this.webhookVerifyToken = webhookVerifyToken;
    }

    public String getWebhookPath() {
        return webhookPath;
    }

    public void setWebhookPath(String webhookPath) {
        this.webhookPath = webhookPath;
    }

    @Override
    public String toString() {
        return "WhatsAppConfiguration{" + "authorizationToken='" + authorizationToken + '\'' + ", baseUri='" + baseUri + '\''
               + ", apiVersion='" + apiVersion + '\''
               + ", phoneNumberId='" + phoneNumberId + '\'' + '}';
    }
}
