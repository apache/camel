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
package org.apache.camel.component.salesforce;

import org.apache.camel.support.jsse.KeyStoreParameters;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SalesforceLoginConfigTest {

    final SalesforceLoginConfig jwt;

    final SalesforceLoginConfig refreshToken;

    final SalesforceLoginConfig usernamePassword;

    public SalesforceLoginConfigTest() {
        usernamePassword = new SalesforceLoginConfig();
        usernamePassword.setUserName("userName");
        usernamePassword.setPassword("password");
        usernamePassword.setClientId("clientId");
        usernamePassword.setClientSecret("clientSecret");

        refreshToken = new SalesforceLoginConfig();
        refreshToken.setUserName("userName");
        refreshToken.setRefreshToken("refreshToken");
        refreshToken.setClientId("clientId");
        refreshToken.setClientSecret("clientSecret");

        jwt = new SalesforceLoginConfig();
        jwt.setUserName("userName");
        final KeyStoreParameters keystore = new KeyStoreParameters();
        keystore.setResource("keystore.jks");
        jwt.setKeystore(keystore);
        jwt.setClientId("clientId");
    }

    @Test
    public void shouldDetermineProperAuthenticationType() {
        assertEquals(AuthenticationType.USERNAME_PASSWORD, usernamePassword.getType());

        assertEquals(AuthenticationType.REFRESH_TOKEN, refreshToken.getType());

        assertEquals(AuthenticationType.JWT, jwt.getType());
    }

    @Test
    public void shouldJwtParameters() {
        jwt.validate();
    }

    @Test
    public void shouldValidateRefreshTokenParameters() {
        refreshToken.validate();
    }

    @Test
    public void shouldValidateUsernamePasswordParameters() {
        usernamePassword.validate();
    }
}
