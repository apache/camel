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
package org.apache.camel.component.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.sshd.common.util.io.IoUtils;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ResourceBasedSSHKeyVerifierTest {

    public static final Logger LOG = LoggerFactory.getLogger(ResourceBasedSSHKeyVerifierTest.class);

    @Test
    public void verifyAllTypesOfPublicKeys() throws IOException {
        final Map<String, String> keys = new LinkedHashMap<>();
        InputStream knownHosts = getClass().getResourceAsStream("/known_hosts_types");
        assertNotNull(knownHosts);
        IoUtils.readAllLines(knownHosts).forEach(l -> {
            String[] tab = l.split(" ");
            if (l.startsWith("#")) {
                return;
            }
            keys.put(tab[1], tab[2]);
        });

        ResourceBasedSSHKeyVerifier verifier = new ResourceBasedSSHKeyVerifier(null, null);
        keys.forEach((type, data) -> {
            LOG.info("Checking key {}", type);
            try {
                PublicKey publicKey = verifier.loadKey(data);
                assertNotNull(publicKey);
            } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
    }
}
