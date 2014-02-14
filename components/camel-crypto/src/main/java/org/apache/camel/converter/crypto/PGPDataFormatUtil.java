/**
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;
import org.bouncycastle.bcpg.sig.KeyFlags;
import org.bouncycastle.openpgp.PGPEncryptedDataList;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPObjectFactory;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyEncryptedData;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureSubpacketVector;
import org.bouncycastle.openpgp.PGPUtil;
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyDecryptorBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.DSA;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.ECDSA;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.ELGAMAL_GENERAL;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.RSA_GENERAL;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.RSA_SIGN;

public final class PGPDataFormatUtil {

    private static final Logger LOG = LoggerFactory.getLogger(PGPDataFormatUtil.class);

    private PGPDataFormatUtil() {
    }

    @Deprecated
    public static PGPPublicKey findPublicKey(CamelContext context, String filename, String userid, boolean forEncryption)
        throws IOException, PGPException, NoSuchProviderException {
        return findPublicKey(context, filename, null, userid, forEncryption);
    }

    @Deprecated
    public static PGPPublicKey findPublicKey(CamelContext context, String filename, byte[] keyRing, String userid, boolean forEncryption)
        throws IOException, PGPException, NoSuchProviderException {

        InputStream is = determineKeyRingInputStream(context, filename, keyRing, forEncryption);

        try {
            List<PGPPublicKey> result = findPublicKeys(is, Collections.singletonList(userid), forEncryption);
            if (result.isEmpty()) {
                return null;
            } else {
                return result.get(0);
            }
        } finally {
            IOHelper.close(is);
        }

    }

    public static List<PGPPublicKey> findPublicKeys(CamelContext context, String filename, byte[] keyRing, List<String> userids,
            boolean forEncryption) throws IOException, PGPException, NoSuchProviderException {
        InputStream is = determineKeyRingInputStream(context, filename, keyRing, forEncryption);
        try {
            return findPublicKeys(is, userids, forEncryption);
        } finally {
            IOHelper.close(is);
        }
    }

    public static PGPPublicKey findPublicKeyWithKeyId(CamelContext context, String filename, byte[] keyRing, long keyid,
            boolean forEncryption) throws IOException, PGPException, NoSuchProviderException {
        InputStream is = determineKeyRingInputStream(context, filename, keyRing, forEncryption);
        PGPPublicKey pubKey;
        try {
            pubKey = findPublicKeyWithKeyId(is, keyid);
        } finally {
            IOHelper.close(is);
        }
        return pubKey;
    }

    public static PGPPrivateKey findPrivateKeyWithKeyId(CamelContext context, String filename, byte[] secretKeyRing, long keyid,
            String passphrase, PGPPassphraseAccessor passpraseAccessor, String provider) throws IOException, PGPException,
            NoSuchProviderException {
        InputStream is = determineKeyRingInputStream(context, filename, secretKeyRing, true);
        try {
            return findPrivateKeyWithKeyId(is, keyid, passphrase, passpraseAccessor, provider);
        } finally {
            IOHelper.close(is);
        }
    }

    @SuppressWarnings("unchecked")
    private static PGPPrivateKey findPrivateKeyWithKeyId(InputStream keyringInput, long keyid, String passphrase,
            PGPPassphraseAccessor passphraseAccessor, String provider) throws IOException, PGPException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput));
        for (Iterator<?> i = pgpSec.getKeyRings(); i.hasNext();) {
            Object data = i.next();
            if (data instanceof PGPSecretKeyRing) {
                PGPSecretKeyRing keyring = (PGPSecretKeyRing) data;
                PGPSecretKey secKey = keyring.getSecretKey(keyid);
                if (secKey != null) {
                    if (passphrase == null && passphraseAccessor != null) {
                        // get passphrase from accessor // only primary/master key has user IDS
                        Iterator<String> userIDs = keyring.getSecretKey().getUserIDs();
                        while (passphrase == null && userIDs.hasNext()) {
                            passphrase = passphraseAccessor.getPassphrase(userIDs.next());
                        }
                    }
                    if (passphrase != null) {
                        PGPPrivateKey privateKey = secKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider(provider)
                                .build(passphrase.toCharArray()));
                        if (privateKey != null) {
                            return privateKey;
                        }
                    }
                }

            }
        }
        return null;
    }

    private static InputStream determineKeyRingInputStream(CamelContext context, String filename, byte[] keyRing, boolean forEncryption)
        throws IOException {
        if (filename != null && keyRing != null) {
            String encryptionOrSignature;
            if (forEncryption) {
                encryptionOrSignature = "encryption";
            } else {
                encryptionOrSignature = "signature";
            }
            throw new IllegalStateException(String.format("Either specify %s file name or key ring byte array. You can not specify both.",
                    encryptionOrSignature));
        }
        InputStream is;
        if (keyRing != null) {
            is = new ByteArrayInputStream(keyRing);
        } else {
            is = ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), filename);
        }
        return is;
    }

    @SuppressWarnings("unchecked")
    private static PGPPublicKey findPublicKeyWithKeyId(InputStream input, long keyid) throws IOException, PGPException,
            NoSuchProviderException {
        PGPPublicKeyRingCollection pgpSec = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(input));

        for (Iterator<PGPPublicKeyRing> keyRingIter = pgpSec.getKeyRings(); keyRingIter.hasNext();) {
            PGPPublicKeyRing keyRing = keyRingIter.next();
            PGPPublicKey key = keyRing.getPublicKey(keyid);
            if (key != null) {
                return key;
            }

        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<PGPPublicKey> findPublicKeys(InputStream input, List<String> userids, boolean forEncryption) throws IOException,
            PGPException, NoSuchProviderException {
        List<PGPPublicKey> result = new ArrayList<PGPPublicKey>(3);

        PGPPublicKeyRingCollection pgpSec = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(input));

        for (Iterator<PGPPublicKeyRing> keyRingIter = pgpSec.getKeyRings(); keyRingIter.hasNext();) {
            PGPPublicKeyRing keyRing = keyRingIter.next();
            Set<String> keyUserIds = getUserIds(keyRing);
            for (Iterator<PGPPublicKey> keyIter = keyRing.getPublicKeys(); keyIter.hasNext();) {
                PGPPublicKey key = keyIter.next();
                for (String useridPart : userids) {
                    for (String keyUserId : keyUserIds) {
                        if (keyUserId != null && keyUserId.contains(useridPart)) {
                            if (forEncryption) {
                                if (isEncryptionKey(key)) {
                                    LOG.debug(
                                            "Public encryption key with key user ID {} and key ID {} found for specified user ID part {}",
                                            new Object[] {keyUserId, Long.toString(key.getKeyID()), useridPart });
                                    result.add(key);
                                }
                            } else if (!forEncryption && isSignatureKey(key)) {
                                // not used!
                                result.add(key);
                                LOG.debug("Public key with key user ID {} and key ID {} found for specified user ID part {}", new Object[] {
                                    keyUserId, Long.toString(key.getKeyID()), useridPart });
                            }
                        }
                    }
                }
            }
        }

        return result;
    }

    private static boolean isEncryptionKey(PGPPublicKey key) {
        if (!key.isEncryptionKey()) {
            return false;
        }
        //check keyflags
        Boolean hasEncryptionKeyFlags = hasOneOfExpectedKeyFlags(key, new int[] {KeyFlags.ENCRYPT_COMMS, KeyFlags.ENCRYPT_STORAGE });
        if (hasEncryptionKeyFlags != null && !hasEncryptionKeyFlags) {
            LOG.debug(
                    "Public key with key key ID {} found for specified user ID. But this key will not be used for the encryption, because its key flags are not encryption key flags.",
                    new Object[] {Long.toString(key.getKeyID()) });
            return false;
        } else {
            // also without keyflags (hasEncryptionKeyFlags = null), true is returned!
            return true;
        }

    }

    // Within a public keyring, the master / primary key has the user ID(s); the subkeys don't
    // have user IDs associated directly to them, but the subkeys are implicitly associated with
    // the user IDs of the master / primary key. The master / primary key is the first key in
    // the keyring, and the rest of the keys are subkeys.
    // http://bouncy-castle.1462172.n4.nabble.com/How-to-find-PGP-subkeys-td1465289.html
    @SuppressWarnings("unchecked")
    private static Set<String> getUserIds(PGPPublicKeyRing keyRing) {
        Set<String> userIds = new LinkedHashSet<String>(3);
        for (Iterator<PGPPublicKey> keyIter = keyRing.getPublicKeys(); keyIter.hasNext();) {
            PGPPublicKey key = keyIter.next();
            for (Iterator<String> iterator = key.getUserIDs(); iterator.hasNext();) {
                userIds.add(iterator.next());
            }
        }
        return userIds;
    }

    // Within a secret keyring, the master / primary key has the user ID(s); the subkeys don't
    // have user IDs associated directly to them, but the subkeys are implicitly associated with
    // the user IDs of the master / primary key. The master / primary key is the first key in
    // the keyring, and the rest of the keys are subkeys.
    // http://bouncy-castle.1462172.n4.nabble.com/How-to-find-PGP-subkeys-td1465289.html
    @SuppressWarnings("unchecked")
    private static Set<String> getUserIds(PGPSecretKeyRing keyRing) {
        Set<String> userIds = new LinkedHashSet<String>(3);
        PGPSecretKey key = keyRing.getSecretKey();
        for (Iterator<String> iterator = key.getUserIDs(); iterator.hasNext();) {
            userIds.add(iterator.next());
        }
        return userIds;
    }

    private static boolean isSignatureKey(PGPPublicKey key) {
        int algorithm = key.getAlgorithm();
        return algorithm == RSA_GENERAL || algorithm == RSA_SIGN || algorithm == DSA || algorithm == ECDSA || algorithm == ELGAMAL_GENERAL;
    }

    @Deprecated
    public static PGPPrivateKey findPrivateKey(CamelContext context, String keychainFilename, InputStream encryptedInput, String passphrase)
        throws IOException, PGPException, NoSuchProviderException {
        return findPrivateKey(context, keychainFilename, null, encryptedInput, passphrase, "BC");
    }

    @Deprecated
    public static PGPPrivateKey findPrivateKey(CamelContext context, String keychainFilename, byte[] secKeyRing,
            InputStream encryptedInput, String passphrase, String provider) throws IOException, PGPException, NoSuchProviderException {
        return findPrivateKey(context, keychainFilename, secKeyRing, encryptedInput, passphrase, null, provider);
    }

    @Deprecated
    public static PGPPrivateKey findPrivateKey(CamelContext context, String keychainFilename, byte[] secKeyRing,
            InputStream encryptedInput, String passphrase, PGPPassphraseAccessor passphraseAccessor, String provider) throws IOException,
            PGPException, NoSuchProviderException {

        InputStream keyChainInputStream = determineKeyRingInputStream(context, keychainFilename, secKeyRing, true);
        PGPPrivateKey privKey = null;
        try {
            privKey = findPrivateKey(keyChainInputStream, encryptedInput, passphrase, passphraseAccessor, provider);
        } finally {
            IOHelper.close(keyChainInputStream);
        }
        return privKey;
    }

    @Deprecated
    private static PGPPrivateKey findPrivateKey(InputStream keyringInput, InputStream encryptedInput, String passphrase,
            PGPPassphraseAccessor passphraseAccessor, String provider) throws IOException, PGPException, NoSuchProviderException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput));
        PGPObjectFactory factory = new PGPObjectFactory(PGPUtil.getDecoderStream(encryptedInput));
        PGPEncryptedDataList enc;
        Object o = factory.nextObject();
        if (o == null) {
            throw new PGPException("Provided input is not encrypted.");
        }
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) factory.nextObject();
        }
        encryptedInput.reset(); // nextObject() method reads from the InputStream, so rewind it!
        Iterator<?> encryptedDataObjects = enc.getEncryptedDataObjects();
        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData encryptedData = null;
        while (privateKey == null && encryptedDataObjects.hasNext()) {
            encryptedData = (PGPPublicKeyEncryptedData) encryptedDataObjects.next();
            PGPSecretKey pgpSecKey = pgpSec.getSecretKey(encryptedData.getKeyID());
            if (pgpSecKey != null) {
                if (passphrase == null && passphraseAccessor != null) {
                    // get passphrase from accessor
                    @SuppressWarnings("unchecked")
                    Iterator<String> userIDs = pgpSecKey.getUserIDs();
                    while (passphrase == null && userIDs.hasNext()) {
                        passphrase = passphraseAccessor.getPassphrase(userIDs.next());
                    }
                }
                privateKey = pgpSecKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider(provider).build(
                        passphrase.toCharArray()));
            }
        }
        if (privateKey == null && pgpSec.size() > 0 && encryptedData != null) {
            throw new PGPException("Provided input is encrypted with unknown pair of keys.");
        }
        return privateKey;
    }

    @Deprecated
    public static PGPSecretKey findSecretKey(CamelContext context, String keychainFilename, String passphrase) throws IOException,
            PGPException, NoSuchProviderException {
        return findSecretKey(context, keychainFilename, null, passphrase, "BC");
    }

    @Deprecated
    public static PGPSecretKey findSecretKey(CamelContext context, String keychainFilename, byte[] secKeyRing, String passphrase,
            String userId, String provider) throws IOException, PGPException, NoSuchProviderException {
        InputStream keyChainInputStream = determineKeyRingInputStream(context, keychainFilename, secKeyRing, false);
        try {
            List<PGPSecretKeyAndPrivateKeyAndUserId> secKeys = findSecretKeysWithPrivateKeyAndUserId(keyChainInputStream,
                    Collections.singletonMap(userId, passphrase), provider);
            if (!secKeys.isEmpty()) {
                return secKeys.get(0).getSecretKey();
            }
            return null;
        } finally {
            IOHelper.close(keyChainInputStream);
        }
    }

    public static List<PGPSecretKeyAndPrivateKeyAndUserId> findSecretKeysWithPrivateKeyAndUserId(CamelContext context,
            String keychainFilename, byte[] secKeyRing, Map<String, String> sigKeyUserId2Password, String provider) throws IOException,
            PGPException, NoSuchProviderException {
        InputStream keyChainInputStream = determineKeyRingInputStream(context, keychainFilename, secKeyRing, false);
        try {
            return findSecretKeysWithPrivateKeyAndUserId(keyChainInputStream, sigKeyUserId2Password, provider);
        } finally {
            IOHelper.close(keyChainInputStream);
        }
    }

    @Deprecated
    public static PGPSecretKey findSecretKey(CamelContext context, String keychainFilename, byte[] secKeyRing, String passphrase,
            String provider) throws IOException, PGPException, NoSuchProviderException {

        return findSecretKey(context, keychainFilename, secKeyRing, passphrase, null, provider);
    }

    @SuppressWarnings("unchecked")
    private static List<PGPSecretKeyAndPrivateKeyAndUserId> findSecretKeysWithPrivateKeyAndUserId(InputStream keyringInput,
            Map<String, String> sigKeyUserId2Password, String provider) throws IOException, PGPException, NoSuchProviderException {
        List<PGPSecretKeyAndPrivateKeyAndUserId> result = new ArrayList<PGPDataFormatUtil.PGPSecretKeyAndPrivateKeyAndUserId>(
                sigKeyUserId2Password.size());
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput));
        for (Iterator<?> i = pgpSec.getKeyRings(); i.hasNext();) {
            Object data = i.next();
            if (data instanceof PGPSecretKeyRing) {
                PGPSecretKeyRing keyring = (PGPSecretKeyRing) data;
                Set<String> keyUserIds = getUserIds(keyring);

                for (String userIdPart : sigKeyUserId2Password.keySet()) {
                    for (String keyUserId : keyUserIds) {
                        if (keyUserId.contains(userIdPart)) {
                            for (Iterator<PGPSecretKey> iterKey = keyring.getSecretKeys(); iterKey.hasNext();) {
                                PGPSecretKey secKey = iterKey.next();
                                if (isSigningKey(secKey)) {
                                    PGPPrivateKey privateKey = secKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider(
                                            provider).build(sigKeyUserId2Password.get(userIdPart).toCharArray()));
                                    if (privateKey != null) {
                                        result.add(new PGPSecretKeyAndPrivateKeyAndUserId(secKey, privateKey, keyUserId));
                                        LOG.debug("Private key with key user ID {} and key ID {} found for specified user ID part {}",
                                                new Object[] {keyUserId, Long.toString(privateKey.getKeyID()), userIdPart });

                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    private static boolean isSigningKey(PGPSecretKey secKey) {
        if (!secKey.isSigningKey()) {
            return false;
        }
        Boolean hasSigningKeyFlag = hasOneOfExpectedKeyFlags(secKey.getPublicKey(), new int[] {KeyFlags.SIGN_DATA });
        if (hasSigningKeyFlag != null && !hasSigningKeyFlag) {
            // not a signing key --> ignore
            LOG.debug(
                    "Secret key with key ID {} found for specified user ID part. But this key will not be used for signing because of its key flags.",
                    Long.toString(secKey.getKeyID()));
            return false;
        } else {
            // also if there are not any keyflags (hasSigningKeyFlag=null),  true is returned!
            return true;
        }

    }

    /**
     * Checks whether one of the signatures of the key has one of the expected
     * key flags
     * 
     * @param key
     * @return {@link Boolean#TRUE} if key has one of the expected flag,
     *         <code>null</code> if the key does not have any key flags,
     *         {@link Boolean#FALSE} if the key has none of the expected flags
     */
    private static Boolean hasOneOfExpectedKeyFlags(PGPPublicKey key, int[] expectedKeyFlags) {
        boolean containsKeyFlags = false;
        for (@SuppressWarnings("unchecked")
        Iterator<PGPSignature> itsig = key.getSignatures(); itsig.hasNext();) {
            PGPSignature sig = itsig.next();
            PGPSignatureSubpacketVector subPacks = sig.getHashedSubPackets();
            if (subPacks != null) {
                int keyFlag = subPacks.getKeyFlags();
                if (keyFlag > 0 && !containsKeyFlags) {
                    containsKeyFlags = true;
                }
                for (int expectdKeyFlag : expectedKeyFlags) {
                    int result = keyFlag & expectdKeyFlag;
                    if (result == expectdKeyFlag) {
                        return Boolean.TRUE;
                    }
                }
            }
        }
        if (containsKeyFlags) {
            return Boolean.FALSE;
        }
        return null; // no key flag
    }

    public static class PGPSecretKeyAndPrivateKeyAndUserId {

        private final PGPSecretKey secretKey;

        private final PGPPrivateKey privateKey;

        private final String userId;

        public PGPSecretKeyAndPrivateKeyAndUserId(PGPSecretKey secretKey, PGPPrivateKey privateKey, String userId) {
            this.secretKey = secretKey;
            this.privateKey = privateKey;
            this.userId = userId;
        }

        public PGPSecretKey getSecretKey() {
            return secretKey;
        }

        public PGPPrivateKey getPrivateKey() {
            return privateKey;
        }

        public String getUserId() {
            return userId;
        }

    }
}
