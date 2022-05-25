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

public final class WhatsAppApiConfig {

    private final String authorizationToken;
    private final int port;
    private final String baseUri;
    private final String phoneNumberId;
    private final String apiVersion;
    private final String recipientPhoneNumber;

    public WhatsAppApiConfig(String baseUri, int port, String authorizationToken, String phoneNumberId, String apiVersion,
                             String recipientPhoneNumber) {
        this.baseUri = baseUri;
        this.port = port;
        this.authorizationToken = authorizationToken;
        this.phoneNumberId = phoneNumberId;
        this.apiVersion = apiVersion;
        this.recipientPhoneNumber = recipientPhoneNumber;
    }

    public static WhatsAppApiConfig fromEnv() {
        final String authorizationToken = System.getenv("WHATSAPP_AUTHORIZATION_TOKEN");
        final String phoneNumberId = System.getenv("WHATSAPP_PHONE_NUMBER_ID");
        final String recipientPhoneNumber = System.getenv("WHATSAPP_RECIPIENT_PHONE_NUMBER");

        return new WhatsAppApiConfig(
                WhatsAppComponent.API_DEFAULT_URL, 443, authorizationToken, phoneNumberId,
                WhatsAppComponent.API_DEFAULT_VERSION, recipientPhoneNumber);
    }

    public static WhatsAppApiConfig mock(int port) {
        return new WhatsAppApiConfig("http://localhost:" + port, port, "mock-token", "-1", "v1", "1");
    }

    public String getAuthorizationToken() {
        return authorizationToken;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public int getPort() {
        return port;
    }

    public String getPhoneNumberId() {
        return phoneNumberId;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getRecipientPhoneNumber() {
        return recipientPhoneNumber;
    }
}
