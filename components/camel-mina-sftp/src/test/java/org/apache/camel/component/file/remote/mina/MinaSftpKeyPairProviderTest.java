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
package org.apache.camel.component.file.remote.mina;

import java.io.File;
import java.nio.file.Files;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for MinaSftpKeyPairProvider.
 */
public class MinaSftpKeyPairProviderTest {

    private static final String TEST_RESOURCES = "src/test/resources/";

    private CamelContext context;

    @BeforeEach
    public void setUp() throws Exception {
        context = new DefaultCamelContext();
        context.start();
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (context != null) {
            context.stop();
        }
    }

    @Test
    public void testNullWhenNoKeyConfigured() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();

        KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNull(keyPair, "Should return null when no key is configured");
    }

    @Test
    public void testLoadFromDirectKeyPair() throws Exception {
        // Generate a test KeyPair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair generatedKeyPair = keyGen.generateKeyPair();

        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setKeyPair(generatedKeyPair);

        KeyPair loadedKeyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNotNull(loadedKeyPair, "KeyPair should not be null");
        assertEquals(generatedKeyPair, loadedKeyPair, "Should return the same KeyPair instance");
    }

    @Test
    public void testLoadFromFile() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKeyFile(TEST_RESOURCES + "test-key-rsa");

        KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNotNull(keyPair, "KeyPair should not be null");
        assertEquals("RSA", keyPair.getPublic().getAlgorithm(), "Should be RSA key");
    }

    @Test
    public void testLoadFromBytes() throws Exception {
        byte[] keyBytes = Files.readAllBytes(new File(TEST_RESOURCES + "test-key-rsa").toPath());

        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKey(keyBytes);

        KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNotNull(keyPair, "KeyPair should not be null");
        assertEquals("RSA", keyPair.getPublic().getAlgorithm(), "Should be RSA key");
    }

    @Test
    public void testLoadFromUri() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKeyUri("file:" + TEST_RESOURCES + "test-key-rsa");

        KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNotNull(keyPair, "KeyPair should not be null");
        assertEquals("RSA", keyPair.getPublic().getAlgorithm(), "Should be RSA key");
    }

    @Test
    public void testLoadEncryptedKey() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKeyFile(TEST_RESOURCES + "test-key-rsa-encrypted");
        config.setPrivateKeyPassphrase("testpass");

        KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNotNull(keyPair, "KeyPair should not be null");
        assertEquals("RSA", keyPair.getPublic().getAlgorithm(), "Should be RSA key");
    }

    @Test
    public void testLoadEncryptedKeyWithWrongPassphrase() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKeyFile(TEST_RESOURCES + "test-key-rsa-encrypted");
        config.setPrivateKeyPassphrase("wrongpassword");

        GenericFileOperationFailedException exception = assertThrows(
                GenericFileOperationFailedException.class,
                () -> MinaSftpKeyPairProvider.loadKeyPair(config, context));

        assertTrue(exception.getMessage().contains("Failed to load private key"),
                "Should indicate key loading failure");
    }

    @Test
    public void testLoadEd25519Key() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKeyFile(TEST_RESOURCES + "test-key-ed25519");

        KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNotNull(keyPair, "KeyPair should not be null");
        // Ed25519 keys report as EdDSA in Java
        assertTrue(keyPair.getPublic().getAlgorithm().contains("Ed"),
                "Should be Ed25519 key, got: " + keyPair.getPublic().getAlgorithm());
    }

    @Test
    public void testLoadEcdsaKey() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKeyFile(TEST_RESOURCES + "test-key-ecdsa");

        KeyPair keyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        assertNotNull(keyPair, "KeyPair should not be null");
        assertEquals("EC", keyPair.getPublic().getAlgorithm(), "Should be ECDSA key");
    }

    @Test
    public void testLoadFromNonExistentFile() {
        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setPrivateKeyFile(TEST_RESOURCES + "non-existent-key");

        GenericFileOperationFailedException exception = assertThrows(
                GenericFileOperationFailedException.class,
                () -> MinaSftpKeyPairProvider.loadKeyPair(config, context));

        assertTrue(exception.getMessage().contains("does not exist"),
                "Should indicate file does not exist");
    }

    @Test
    public void testPriorityDirectKeyPairOverFile() throws Exception {
        // Generate a test KeyPair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        KeyPair generatedKeyPair = keyGen.generateKeyPair();

        MinaSftpConfiguration config = new MinaSftpConfiguration();
        config.setKeyPair(generatedKeyPair);
        config.setPrivateKeyFile(TEST_RESOURCES + "test-key-ed25519"); // This should be ignored

        KeyPair loadedKeyPair = MinaSftpKeyPairProvider.loadKeyPair(config, context);

        // Should use the direct KeyPair, not load from file
        assertEquals(generatedKeyPair, loadedKeyPair, "Direct KeyPair should take priority");
    }
}
