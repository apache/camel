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
package org.apache.camel.component.http;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.CredentialsStore;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.utils.Base64;

public final class HttpCredentialsHelper {

    private final CredentialsStore credentialsProvider;

    HttpCredentialsHelper() {
        this.credentialsProvider = new BasicCredentialsProvider();
    }

    public CredentialsProvider getCredentialsProvider(
            String host, Integer port, Credentials credentials) {
        this.credentialsProvider.setCredentials(new AuthScope(
                host,
                Objects.requireNonNullElse(port, -1)), credentials);
        return credentialsProvider;
    }

    public static String generateBasicAuthHeader(String user, String pass) {
        final String auth = user + ":" + pass;
        final byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(StandardCharsets.UTF_8));
        return "Basic " + new String(encodedAuth);
    }

    public static Credentials getCredentials(String method, String username, String password, String host, String domain) {
        if (username != null && password != null) {
            if (domain != null && host != null) {
                return new NTCredentials(username, password.toCharArray(), host, domain);
            } else {
                return new UsernamePasswordCredentials(username, password.toCharArray());
            }
        }
        return null;
    }

}
