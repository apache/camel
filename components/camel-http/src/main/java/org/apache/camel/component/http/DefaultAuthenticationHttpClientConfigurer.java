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

import java.util.List;

import org.apache.hc.client5.http.auth.AuthSchemeFactory;
import org.apache.hc.client5.http.auth.BearerToken;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.StandardAuthScheme;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicSchemeFactory;
import org.apache.hc.client5.http.impl.auth.BearerSchemeFactory;
import org.apache.hc.client5.http.impl.auth.DigestSchemeFactory;
import org.apache.hc.client5.http.impl.auth.NTLMSchemeFactory;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.config.RegistryBuilder;

public class DefaultAuthenticationHttpClientConfigurer implements HttpClientConfigurer {

    private final String username;
    private final char[] password;
    private final String domain;
    private final String host;
    private final String bearerToken;
    private final HttpCredentialsHelper credentialsHelper;

    public DefaultAuthenticationHttpClientConfigurer(
            String user,
            String pwd,
            String domain,
            String host,
            String bearerToken,
            HttpCredentialsHelper credentialsHelper) {
        this.username = user;
        this.password = pwd == null ? new char[0] : pwd.toCharArray();
        this.domain = domain;
        this.host = host;
        this.bearerToken = bearerToken;
        this.credentialsHelper = credentialsHelper;
    }

    @Override
    public void configureHttpClient(HttpClientBuilder clientBuilder) {
        Credentials defaultcreds;
        if (domain != null) {
            defaultcreds = new NTCredentials(username, password, host, domain);
            // NTLM is not included by default so we need to rebuild the registry to include NTLM
            var autoSchemes = RegistryBuilder.<AuthSchemeFactory>create()
                    .register(StandardAuthScheme.BASIC, BasicSchemeFactory.INSTANCE)
                    .register(StandardAuthScheme.DIGEST, DigestSchemeFactory.INSTANCE)
                    .register(StandardAuthScheme.BEARER, BearerSchemeFactory.INSTANCE)
                    .register(StandardAuthScheme.NTLM, NTLMSchemeFactory.INSTANCE)
                    .build();

            // Set NTLM as preferred scheme
            RequestConfig requestConfig = RequestConfig.custom()
                    .setTargetPreferredAuthSchemes(List.of(StandardAuthScheme.NTLM))
                    .build();

            clientBuilder.setDefaultAuthSchemeRegistry(autoSchemes).setDefaultRequestConfig(requestConfig);

            clientBuilder.setDefaultAuthSchemeRegistry(autoSchemes);
        } else if (bearerToken != null) {
            defaultcreds = new BearerToken(bearerToken);
        } else {
            defaultcreds = new UsernamePasswordCredentials(username, password);
        }
        clientBuilder.setDefaultCredentialsProvider(credentialsHelper.getCredentialsProvider(host, null, defaultcreds));
    }
}
