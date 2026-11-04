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
package org.apache.camel.component.cyberark.vault;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An endpoint without usable credentials fails to start, with a message that names the options to set, instead of
 * failing on the first message.
 */
class CyberArkVaultEndpointCredentialsTest extends CamelTestSupport {

    private static final String CONJUR = "url=http://localhost:1&account=myaccount";

    @Test
    void apiKeyWithoutUsernameFailsAtStart() throws Exception {
        Endpoint endpoint = context.getComponent("cyberark-vault")
                .createEndpoint("cyberark-vault:secret?secretId=db/password&" + CONJUR + "&apiKey=my-api-key");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::start);
        assertNamesTheOptions(e);
        assertFalse(e.getMessage().contains("my-api-key"), e.getMessage());
    }

    @Test
    void noCredentialsFailsAtStart() {
        CyberArkVaultConfiguration configuration = new CyberArkVaultConfiguration();
        configuration.setUrl("http://localhost:1");
        configuration.setAccount("myaccount");
        configuration.setSecretId("db/password");
        CyberArkVaultEndpoint endpoint = new CyberArkVaultEndpoint(
                "cyberark-vault:secret", context.getComponent("cyberark-vault"), configuration);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, endpoint::start);
        assertNamesTheOptions(e);
    }

    @Test
    void usernameAndApiKeyStart() throws Exception {
        CyberArkVaultEndpoint endpoint = (CyberArkVaultEndpoint) context.getComponent("cyberark-vault")
                .createEndpoint("cyberark-vault:secret?secretId=db/password&" + CONJUR
                                + "&username=host/myapp&apiKey=my-api-key");

        endpoint.start();
        try {
            assertNotNull(endpoint.getConjurClient());
        } finally {
            endpoint.stop();
        }
    }

    private static void assertNamesTheOptions(IllegalArgumentException e) {
        String message = e.getMessage();
        assertTrue(message.contains("authToken"), message);
        assertTrue(message.contains("username and apiKey"), message);
        assertTrue(message.contains("username and password"), message);
        assertTrue(message.contains("conjurClient"), message);
    }
}
