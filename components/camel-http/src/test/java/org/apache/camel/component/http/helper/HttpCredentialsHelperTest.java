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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HttpCredentialsHelperTest {

    private final String host = "credentials.test.org";
    private final Integer port = Integer.valueOf(80);
    private final String proxyHost = "proxy.test.org";
    private final Integer proxyPort = Integer.valueOf(8080);
    private String username = "testUser";
    private String password = "testPassowrd";

    private String proxyUsername = "proxyUser";
    private String proxyPassword = "proxyPassowrd";

    @Test
    void testOneCredential() {
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        CredentialsProvider provider = HttpCredentialsHelper.getCredentialsProvider(host, port, credentials);
        assertNotNull(provider);
        assertEquals(credentials, provider.getCredentials(new AuthScope(host, port)));
    }

    @Test
    void testCredentialsNoPort() {
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        CredentialsProvider provider = HttpCredentialsHelper.getCredentialsProvider(host, null, credentials);
        assertNotNull(provider);
        assertEquals(credentials, provider.getCredentials(new AuthScope(host, AuthScope.ANY_PORT)));
    }

    @Test
    void testTwoCredentials() {
        Credentials credentials = new UsernamePasswordCredentials(username, password);
        CredentialsProvider provider = HttpCredentialsHelper.getCredentialsProvider(host, port, credentials);
        Credentials proxyCredentials = new UsernamePasswordCredentials(proxyUsername, proxyPassword);
        CredentialsProvider provider2 = HttpCredentialsHelper.getCredentialsProvider(proxyHost, proxyPort,
                proxyCredentials);

        assertNotNull(provider);
        assertEquals(provider, provider2);
        assertEquals(credentials, provider.getCredentials(new AuthScope(host, port)));
        assertEquals(proxyCredentials, provider.getCredentials(new AuthScope(proxyHost, proxyPort)));

    }

}
