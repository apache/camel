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
package org.apache.camel.component.http.helper;

import java.util.Map;
import java.util.Objects;

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;

public final class HttpCredentialsHelper {

    private static final Map<HttpClientBuilder, CredentialsProvider> CREDENTIAL_PROVIDER_MAP = new java.util.HashMap<>();

    private HttpCredentialsHelper() {
        // helper class
    }

    public static CredentialsProvider getCredentialsProvider(
            HttpClientBuilder builder, String host, Integer port, Credentials credentials) {
        CredentialsProvider credentialsProvider = CREDENTIAL_PROVIDER_MAP.get(builder);
        if (credentialsProvider == null) {
            credentialsProvider = new BasicCredentialsProvider();
            CREDENTIAL_PROVIDER_MAP.put(builder, credentialsProvider);
        }
        credentialsProvider.setCredentials(new AuthScope(
                host,
                Objects.requireNonNullElse(port, AuthScope.ANY_PORT)), credentials);
        return credentialsProvider;
    }

}
