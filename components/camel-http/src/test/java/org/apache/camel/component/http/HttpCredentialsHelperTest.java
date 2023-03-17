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

import org.apache.camel.test.junit5.CamelTestSupport;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.core5.http.protocol.BasicHttpContext;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HttpCredentialsHelperTest extends CamelTestSupport {

    private final HttpCredentialsHelper credentialsHelper = new HttpCredentialsHelper();
    private final HttpContext context = new BasicHttpContext();

    private final String host = "credentials.test.org";
    private final Integer port = 80;
    private final String proxyHost = "proxy.test.org";
    private final Integer proxyPort = 8080;
    private final String username = "testUser";
    private final String password = "testPassowrd";

    @Test
    void testOneCredential() {
        Credentials credentials = new UsernamePasswordCredentials(username, password.toCharArray());
        CredentialsProvider provider = credentialsHelper.getCredentialsProvider(host, port, credentials);
        assertNotNull(provider);
        assertEquals(credentials, provider.getCredentials(new AuthScope(host, port), context));
    }

    @Test
    void testCredentialsNoPort() {
        Credentials credentials = new UsernamePasswordCredentials(username, password.toCharArray());
        CredentialsProvider provider = credentialsHelper.getCredentialsProvider(host, null, credentials);
        assertNotNull(provider);
        assertEquals(credentials, provider.getCredentials(new AuthScope(host, -1), context));
    }

    @Test
    void testTwoCredentials() {
        Credentials credentials = new UsernamePasswordCredentials(username, password.toCharArray());
        CredentialsProvider provider = credentialsHelper.getCredentialsProvider(host, port, credentials);
        String proxyPassword = "proxyPassowrd";
        String proxyUsername = "proxyUser";
        Credentials proxyCredentials = new UsernamePasswordCredentials(proxyUsername, proxyPassword.toCharArray());
        CredentialsProvider provider2 = credentialsHelper.getCredentialsProvider(proxyHost, proxyPort,
                proxyCredentials);

        assertNotNull(provider);
        assertEquals(provider, provider2);
        assertEquals(credentials, provider.getCredentials(new AuthScope(host, port), context));
        assertEquals(proxyCredentials, provider.getCredentials(new AuthScope(proxyHost, proxyPort), context));

    }

    @Test
    void testTwoCredentialsDifferentHttpComponents() {
        Credentials credentials1 = new UsernamePasswordCredentials(username, password.toCharArray());
        CredentialsProvider provider1 = credentialsHelper.getCredentialsProvider(host, port, credentials1);
        Credentials credentials2 = new UsernamePasswordCredentials(username + "2", (password + "2").toCharArray());

        CredentialsProvider provider2
                = new HttpCredentialsHelper().getCredentialsProvider(proxyHost, proxyPort, credentials2);
        assertNotEquals(provider1, provider2);
        assertEquals(credentials1, provider1.getCredentials(new AuthScope(host, port), context));
        assertEquals(credentials2, provider2.getCredentials(new AuthScope(proxyHost, proxyPort), context));
        assertNull(provider2.getCredentials(new AuthScope(host, port), context));
        assertNull(provider1.getCredentials(new AuthScope(proxyHost, proxyPort), context));
    }

}
