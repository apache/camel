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
package org.apache.camel.component.smb;

import com.hierynomus.smbj.auth.AuthenticationContext;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The credentials the smb component logs in with when the username or the password is not set.
 */
class SmbAuthenticationContextTest {

    @Test
    void usernameAndPassword() {
        AuthenticationContext ac = SmbOperations.createAuthenticationContext(configuration("scott", "tiger", "WORKGROUP"));

        assertEquals("scott", ac.getUsername());
        assertArrayEquals("tiger".toCharArray(), ac.getPassword());
        assertEquals("WORKGROUP", ac.getDomain());
    }

    @Test
    void usernameWithoutPasswordHasEmptyPassword() {
        AuthenticationContext ac = SmbOperations.createAuthenticationContext(configuration("scott", null, null));

        assertEquals("scott", ac.getUsername());
        assertEquals(0, ac.getPassword().length);
    }

    @Test
    void guestWithoutPasswordIsGuestLogin() {
        AuthenticationContext ac = SmbOperations.createAuthenticationContext(configuration("Guest", null, null));

        assertTrue(ac.isGuest());
    }

    @ParameterizedTest
    @NullAndEmptySource
    void noUsernameFails(String username) {
        SmbConfiguration configuration = configuration(username, "tiger", null);

        GenericFileOperationFailedException e = assertThrows(GenericFileOperationFailedException.class,
                () -> SmbOperations.createAuthenticationContext(configuration));
        assertEquals("Cannot connect to: localhost:445 as no username is set. Set the username and password of the share,"
                     + " or username=Guest for guest access.",
                e.getMessage());
    }

    private static SmbConfiguration configuration(String username, String password, String domain) {
        SmbConfiguration configuration = new SmbConfiguration();
        configuration.setHostname("localhost");
        configuration.setUsername(username);
        configuration.setPassword(password);
        configuration.setDomain(domain);
        return configuration;
    }
}
