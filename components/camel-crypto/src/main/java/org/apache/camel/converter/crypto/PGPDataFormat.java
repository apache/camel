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
package org.apache.camel.converter.crypto;

import java.io.IOException;
import java.security.NoSuchProviderException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.spi.annotations.Dataformat;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;

/**
 * <code>PGPDataFormat</code> uses the <a
 * href="http://www.bouncycastle.org/java.htm">bouncy castle</a> libraries to
 * enable encryption and decryption in the PGP format.
 * <p>
 * See also {@link PGPKeyAccessDataFormat}.
 */
@Dataformat("pgp")
public class PGPDataFormat extends PGPKeyAccessDataFormat implements PGPPublicKeyAccessor, PGPSecretKeyAccessor {
    
    public static final String KEY_FILE_NAME = "CamelPGPDataFormatKeyFileName";
    public static final String ENCRYPTION_KEY_RING = "CamelPGPDataFormatEncryptionKeyRing";
    public static final String KEY_PASSWORD = "CamelPGPDataFormatKeyPassword";
    public static final String SIGNATURE_KEY_FILE_NAME = "CamelPGPDataFormatSignatureKeyFileName";
    public static final String SIGNATURE_KEY_RING = "CamelPGPDataFormatSignatureKeyRing";
    public static final String SIGNATURE_KEY_PASSWORD = "CamelPGPDataFormatSignatureKeyPassword";

    //private static final Logger log = LoggerFactory.getLogger(PGPDataFormatChanged.class);

    private String password; // only for decryption
    private String keyFileName;
    // alternatively to the file name you can specify the key ring as byte array
    private byte[] encryptionKeyRing;

    private String signaturePassword; //only for signing, optional if you have several signature keys, then you should use passphaseAccessor
    private String signatureKeyFileName;
    // alternatively to the signature key file name you can specify the signature key ring as byte array
    private byte[] signatureKeyRing;

    private PGPPassphraseAccessor passphraseAccessor; // for signing and decryption with multiple keys

    public PGPDataFormat() {
        publicKeyAccessor = this;
        secretKeyAccessor = this;
    }

    protected String findKeyFileName(Exchange exchange) {
        return exchange.getIn().getHeader(KEY_FILE_NAME, getKeyFileName(), String.class);
    }

    protected byte[] findEncryptionKeyRing(Exchange exchange) {
        return exchange.getIn().getHeader(ENCRYPTION_KEY_RING, getEncryptionKeyRing(), byte[].class);
    }

    protected String findKeyPassword(Exchange exchange) {
        return exchange.getIn().getHeader(KEY_PASSWORD, getPassword(), String.class);
        // the following lines are not needed because the passphrase accessor is taken into account later in the decryption case
        //        if (passphraseAccessor != null) {
        //            return passphraseAccessor.getPassphrase(findKeyUserid(exchange));
        //        } else {
        //            return null;
        //        }
    }

    protected String findSignatureKeyFileName(Exchange exchange) {
        return exchange.getIn().getHeader(SIGNATURE_KEY_FILE_NAME, getSignatureKeyFileName(), String.class);
    }

    protected byte[] findSignatureKeyRing(Exchange exchange) {
        return exchange.getIn().getHeader(SIGNATURE_KEY_RING, getSignatureKeyRing(), byte[].class);
    }

    protected String findSignatureKeyPassword(Exchange exchange) {
        String sigPassword = exchange.getIn().getHeader(SIGNATURE_KEY_PASSWORD, getSignaturePassword(), String.class);
        if (sigPassword != null) {
            return sigPassword;
        }
        if (passphraseAccessor != null) {
            return passphraseAccessor.getPassphrase(findSignatureKeyUserid(exchange));
        } else {
            return null;
        }
    }

    public List<PGPSecretKeyAndPrivateKeyAndUserId> determineSecretKeysWithPrivateKeyAndUserId(Exchange exchange, String sigKeyFileName,
            List<String> sigKeyUserids, String sigKeyPassword, byte[] sigKeyRing) throws IOException, PGPException, NoSuchProviderException {

        Map<String, String> sigKeyUserId2Password = determineSignatureKeyUserId2Password(sigKeyUserids, sigKeyPassword);

        List<PGPSecretKeyAndPrivateKeyAndUserId> sigSecretKeysWithPrivateKeyAndUserId = PGPDataFormatUtil
                .findSecretKeysWithPrivateKeyAndUserId(exchange.getContext(), sigKeyFileName, sigKeyRing, sigKeyUserId2Password,
                        getProvider());

        if (sigSecretKeysWithPrivateKeyAndUserId.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "Cannot PGP sign message. No secret key found for User IDs %s. Either add keys with this User IDs to the secret keyring or change the configured User IDs.",
                            sigKeyUserids));
        }
        return sigSecretKeysWithPrivateKeyAndUserId;
    }

    public Map<String, String> determineSignatureKeyUserId2Password(List<String> sigKeyUserids, String sigKeyPassword) {
        // we want to keep the order of the entries, therefore we use LinkedHashMap
        Map<String, String> sigKeyUserId2Password = new LinkedHashMap<>(sigKeyUserids.size());
        for (String sigKeyUserid : sigKeyUserids) {
            if (sigKeyPassword == null) {
                sigKeyPassword = passphraseAccessor.getPassphrase(sigKeyUserid);
            }
            if (sigKeyPassword == null) {
                throw new IllegalArgumentException(
                        String.format(
                                "No passphrase specified for signature key user ID %s. Either specify a passphrase or remove this user ID from the configuration.",
                                sigKeyUserid));
            }
            sigKeyUserId2Password.put(sigKeyUserid, sigKeyPassword);
        }
        return sigKeyUserId2Password;
    }

    /**
     * Filename of the keyring that will be used for the encryption/decryption,
     * classpathResource. Alternatively you can provide the keyring also as byte
     * array; see method {@link #setEncryptionKeyRing(byte[])}.
     */
    public void setKeyFileName(String keyFileName) {
        this.keyFileName = keyFileName;
    }

    public String getKeyFileName() {
        return keyFileName;
    }

    /**
     * Password used to open the private key in secret keyring for decryption
     * (unmarshaling). See also
     * {@link #setPassphraseAccessor(PGPPassphraseAccessor)}.
     */
    public void setPassword(String password) {
        this.password = password;
    }

    public String getPassword() {
        return password;
    }

    /**
     * Filename of the signature keyring that will be used, classpathResource.
     */
    public void setSignatureKeyFileName(String signatureKeyFileName) {
        this.signatureKeyFileName = signatureKeyFileName;
    }

    public String getSignatureKeyFileName() {
        return signatureKeyFileName;
    }

    /**
     * Password used to open the signature private key during marshaling.
     */
    public void setSignaturePassword(String signaturePassword) {
        this.signaturePassword = signaturePassword;
    }

    public String getSignaturePassword() {
        return signaturePassword;
    }

    public byte[] getEncryptionKeyRing() {
        return encryptionKeyRing;
    }

    /**
     * Keyring used for encryption/decryption as byte array. Alternatively you
     * can also provide the keyring as a file; see method
     * {@link #setKeyFileName(String)}.
     */
    public void setEncryptionKeyRing(byte[] encryptionKeyRing) {
        this.encryptionKeyRing = encryptionKeyRing;
    }

    public byte[] getSignatureKeyRing() {
        return signatureKeyRing;
    }

    /**
     * Keyring used for signing/verifying as byte array. Alternatively you can
     * also provide the keyring as a file; see method
     * {@link #setSignatureKeyFileName(String)}.
     */
    public void setSignatureKeyRing(byte[] signatureKeyRing) {
        this.signatureKeyRing = signatureKeyRing;
    }

    public PGPPassphraseAccessor getPassphraseAccessor() {
        return passphraseAccessor;
    }

    /**
     * Alternative way to provide the passphrases. Especially useful for the
     * unmarshal (decryption) case . If no passphrase can be found from the
     * parameter <tt>password</tt> or <tt>signaturePassword</tt> or from the
     * header {@link #SIGNATURE_KEY_PASSWORD} or {@link #KEY_PASSWORD} then we
     * try to get the password from the passphrase accessor. This is especially
     * useful in the decrypt case, where we chose the private key according to
     * the key Id stored in the encrypted data.
     */
    public void setPassphraseAccessor(PGPPassphraseAccessor passphraseAccessor) {
        this.passphraseAccessor = passphraseAccessor;
    }

    @Override
    public List<PGPPublicKey> getEncryptionKeys(Exchange exchange, List<String> useridParts) throws Exception {
        return PGPDataFormatUtil.findPublicKeys(exchange.getContext(), findKeyFileName(exchange), findEncryptionKeyRing(exchange),
                useridParts, true);
    }

    @Override
    public List<PGPSecretKeyAndPrivateKeyAndUserId> getSignerKeys(Exchange exchange, List<String> useridParts) throws Exception {
        String sigKeyFileName = findSignatureKeyFileName(exchange);

        String sigKeyPassword = findSignatureKeyPassword(exchange);
        byte[] sigKeyRing = findSignatureKeyRing(exchange);

        if ((sigKeyFileName == null && sigKeyRing == null) || useridParts == null || useridParts.isEmpty()
                || (sigKeyPassword == null && passphraseAccessor == null)) {
            return Collections.emptyList();
        }
        return determineSecretKeysWithPrivateKeyAndUserId(exchange, sigKeyFileName, useridParts, sigKeyPassword, sigKeyRing);
    }

    @Override
    public PGPPrivateKey getPrivateKey(Exchange exchange, long keyId) throws Exception {
        return PGPDataFormatUtil.findPrivateKeyWithKeyId(exchange.getContext(), findKeyFileName(exchange), findEncryptionKeyRing(exchange),
                keyId, findKeyPassword(exchange), getPassphraseAccessor(), getProvider());
    }


   
    @Override
    public PGPPublicKey getPublicKey(Exchange exchange, long keyId, List<String> userIdParts) throws Exception {
        PGPPublicKeyRingCollection publicKeyringCollection = PGPDataFormatUtil.getPublicKeyRingCollection(exchange.getContext(), 
                findSignatureKeyFileName(exchange), findSignatureKeyRing(exchange), false);
        return PGPDataFormatUtil.getPublicKeyWithKeyIdAndUserID(keyId, userIdParts, publicKeyringCollection);      
    }

    @Override
    public void setPublicKeyAccessor(PGPPublicKeyAccessor publicKeyAccessor) {
        throw new UnsupportedOperationException("Use PGPKeyAccessDataFormat if you want to set the public key access");
    }

    @Override
    public void setSecretKeyAccessor(PGPSecretKeyAccessor secretKeyAccessor) {
        throw new UnsupportedOperationException("Use PGPKeyAccessDataFormat if you want to set the secret key access");
    }
    
}
