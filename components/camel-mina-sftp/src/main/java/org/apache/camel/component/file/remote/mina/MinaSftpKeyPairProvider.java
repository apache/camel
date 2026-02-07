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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Iterator;

import org.apache.camel.CamelContext;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.support.ResourceHelper;
import org.apache.sshd.common.NamedResource;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for loading KeyPair from various sources using Apache MINA SSHD's SecurityUtils.
 * <p/>
 * Supports loading keys from:
 * <ul>
 * <li>Direct KeyPair object</li>
 * <li>Byte array containing key content</li>
 * <li>File path on filesystem</li>
 * <li>Classpath or file: URI using Camel's ResourceHelper</li>
 * </ul>
 * <p/>
 * Supported formats (via MINA SSHD):
 * <ul>
 * <li>PEM (PKCS#1, PKCS#8, OpenSSH format)</li>
 * <li>OpenSSH native format</li>
 * <li>Encrypted keys (PKCS#8 encrypted requires BouncyCastle)</li>
 * </ul>
 * <p/>
 * Supported algorithms:
 * <ul>
 * <li>RSA (all key sizes)</li>
 * <li>ECDSA (P-256, P-384, P-521)</li>
 * <li>Ed25519</li>
 * <li>DSA</li>
 * </ul>
 */
public final class MinaSftpKeyPairProvider {

    private static final Logger LOG = LoggerFactory.getLogger(MinaSftpKeyPairProvider.class);

    private MinaSftpKeyPairProvider() {
        // Utility class
    }

    /**
     * Load a KeyPair from the given configuration.
     * <p/>
     * Priority order:
     * <ol>
     * <li>Direct KeyPair object (if set)</li>
     * <li>Private key byte array</li>
     * <li>Private key file path</li>
     * <li>Private key URI/classpath resource</li>
     * </ol>
     *
     * @param  config                              the SFTP configuration containing key parameters
     * @param  context                             the Camel context for resource loading
     * @return                                     the loaded KeyPair, or null if no key is configured
     * @throws GenericFileOperationFailedException if key loading fails
     */
    public static KeyPair loadKeyPair(MinaSftpConfiguration config, CamelContext context)
            throws GenericFileOperationFailedException {

        // 1. Direct KeyPair takes priority
        if (config.getKeyPair() != null) {
            LOG.debug("Using provided KeyPair object for authentication");
            return config.getKeyPair();
        }

        // 2. Try byte array
        if (config.getPrivateKey() != null && config.getPrivateKey().length > 0) {
            LOG.debug("Loading KeyPair from byte array");
            return loadFromBytes(config.getPrivateKey(), config.getPrivateKeyPassphrase(), "privateKey");
        }

        // 3. Try file path
        if (config.getPrivateKeyFile() != null && !config.getPrivateKeyFile().isEmpty()) {
            LOG.debug("Loading KeyPair from file: {}", config.getPrivateKeyFile());
            return loadFromFile(config.getPrivateKeyFile(), config.getPrivateKeyPassphrase());
        }

        // 4. Try URI/classpath
        if (config.getPrivateKeyUri() != null && !config.getPrivateKeyUri().isEmpty()) {
            LOG.debug("Loading KeyPair from URI: {}", config.getPrivateKeyUri());
            return loadFromUri(config.getPrivateKeyUri(), config.getPrivateKeyPassphrase(), context);
        }

        LOG.trace("No private key configured");
        return null;
    }

    /**
     * Load a KeyPair from a byte array.
     *
     * @param  keyBytes                            the private key content as bytes
     * @param  passphrase                          the passphrase for encrypted keys (may be null)
     * @param  resourceName                        a name for logging/error messages
     * @return                                     the loaded KeyPair
     * @throws GenericFileOperationFailedException if loading fails
     */
    public static KeyPair loadFromBytes(byte[] keyBytes, String passphrase, String resourceName)
            throws GenericFileOperationFailedException {
        try (InputStream is = new ByteArrayInputStream(keyBytes)) {
            return loadFromStream(is, passphrase, resourceName);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(
                    "Failed to load private key from bytes: " + e.getMessage(), e);
        }
    }

    /**
     * Load a KeyPair from a file path.
     *
     * @param  filePath                            the path to the private key file
     * @param  passphrase                          the passphrase for encrypted keys (may be null)
     * @return                                     the loaded KeyPair
     * @throws GenericFileOperationFailedException if loading fails
     */
    public static KeyPair loadFromFile(String filePath, String passphrase)
            throws GenericFileOperationFailedException {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            throw new GenericFileOperationFailedException(
                    "Private key file does not exist: " + filePath);
        }
        if (!Files.isReadable(path)) {
            throw new GenericFileOperationFailedException(
                    "Private key file is not readable: " + filePath);
        }

        try (InputStream is = Files.newInputStream(path)) {
            return loadFromStream(is, passphrase, filePath);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(
                    "Failed to load private key from file '" + filePath + "': " + e.getMessage(), e);
        }
    }

    /**
     * Load a KeyPair from a URI using Camel's ResourceHelper.
     *
     * @param  uri                                 the URI (classpath:, file:, etc.)
     * @param  passphrase                          the passphrase for encrypted keys (may be null)
     * @param  context                             the Camel context for resource resolution
     * @return                                     the loaded KeyPair
     * @throws GenericFileOperationFailedException if loading fails
     */
    public static KeyPair loadFromUri(String uri, String passphrase, CamelContext context)
            throws GenericFileOperationFailedException {
        try (InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, uri)) {
            return loadFromStream(is, passphrase, uri);
        } catch (IOException e) {
            throw new GenericFileOperationFailedException(
                    "Failed to load private key from URI '" + uri + "': " + e.getMessage(), e);
        }
    }

    /**
     * Load a KeyPair from an InputStream.
     *
     * @param  is                                  the input stream containing key data
     * @param  passphrase                          the passphrase for encrypted keys (may be null)
     * @param  resourceName                        a name for logging/error messages
     * @return                                     the loaded KeyPair
     * @throws GenericFileOperationFailedException if loading fails
     */
    private static KeyPair loadFromStream(InputStream is, String passphrase, String resourceName)
            throws GenericFileOperationFailedException {
        try {
            FilePasswordProvider passwordProvider = passphrase != null
                    ? FilePasswordProvider.of(passphrase)
                    : FilePasswordProvider.EMPTY;

            Iterable<KeyPair> keys = SecurityUtils.loadKeyPairIdentities(
                    null, // session - not needed for loading
                    NamedResource.ofName(resourceName),
                    is,
                    passwordProvider);

            Iterator<KeyPair> iterator = keys.iterator();
            if (!iterator.hasNext()) {
                throw new GenericFileOperationFailedException(
                        "No key pairs found in resource: " + resourceName);
            }

            KeyPair keyPair = iterator.next();
            LOG.debug("Successfully loaded {} key from {}",
                    keyPair.getPublic().getAlgorithm(), resourceName);
            return keyPair;

        } catch (IOException | GeneralSecurityException e) {
            String msg = "Failed to load private key from '" + resourceName + "'";
            if (e.getMessage() != null && e.getMessage().contains("password")) {
                msg += " - check if passphrase is correct for encrypted key";
            }
            throw new GenericFileOperationFailedException(msg + ": " + e.getMessage(), e);
        }
    }
}
