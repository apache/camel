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
import java.util.List;
import java.util.Map;

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
import org.bouncycastle.openpgp.operator.bc.BcKeyFingerprintCalculator;
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

    @Deprecated
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

    public static PGPPublicKeyRingCollection getPublicKeyRingCollection(CamelContext context, String filename, byte[] keyRing, boolean forEncryption) throws IOException, PGPException {
        InputStream is = determineKeyRingInputStream(context, filename, keyRing, forEncryption);
        try {
            return new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(is), new BcKeyFingerprintCalculator());
        } finally {
            IOHelper.close(is);
        }
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

    private static PGPPrivateKey findPrivateKeyWithKeyId(InputStream keyringInput, long keyid, String passphrase,
            PGPPassphraseAccessor passphraseAccessor, String provider) throws IOException, PGPException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput),
                                                                           new BcKeyFingerprintCalculator());
        return findPrivateKeyWithkeyId(keyid, passphrase, passphraseAccessor, provider, pgpSec);
    }

    public static PGPPrivateKey findPrivateKeyWithkeyId(long keyid, String passphrase, PGPPassphraseAccessor passphraseAccessor,
            String provider, PGPSecretKeyRingCollection pgpSec) throws PGPException {
        for (Iterator<?> i = pgpSec.getKeyRings(); i.hasNext();) {
            Object data = i.next();
            if (data instanceof PGPSecretKeyRing) {
                PGPSecretKeyRing keyring = (PGPSecretKeyRing) data;
                PGPSecretKey secKey = keyring.getSecretKey(keyid);
                if (secKey != null) {
                    if (passphrase == null && passphraseAccessor != null) {
                        // get passphrase from accessor // only primary/master key has user IDS
                        @SuppressWarnings("unchecked")
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
            is = ResourceHelper.resolveMandatoryResourceAsInputStream(context, filename);
        }
        return is;
    }

    private static PGPPublicKey findPublicKeyWithKeyId(InputStream input, long keyid) throws IOException, PGPException,
            NoSuchProviderException {
        PGPPublicKeyRingCollection pgpSec = 
            new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(input),
                                           new BcKeyFingerprintCalculator());
        return pgpSec.getPublicKey(keyid);
    }

    private static List<PGPPublicKey> findPublicKeys(InputStream input, List<String> userids, boolean forEncryption) throws IOException,
            PGPException, NoSuchProviderException {

        PGPPublicKeyRingCollection pgpSec = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(input),
                                                                           new BcKeyFingerprintCalculator());

        return findPublicKeys(userids, forEncryption, pgpSec);
    }

    public static List<PGPPublicKey> findPublicKeys(List<String> useridParts, boolean forEncryption, PGPPublicKeyRingCollection pgpPublicKeyringCollection) {
        List<PGPPublicKey> result = new ArrayList<PGPPublicKey>(useridParts.size());
        for (Iterator<PGPPublicKeyRing> keyRingIter = pgpPublicKeyringCollection.getKeyRings(); keyRingIter.hasNext();) {
            PGPPublicKeyRing keyRing = keyRingIter.next();
            PGPPublicKey primaryKey = keyRing.getPublicKey();
            String[] foundKeyUserIdForUserIdPart = findFirstKeyUserIdContainingOneOfTheParts(useridParts, primaryKey);
            if (foundKeyUserIdForUserIdPart == null) {
                LOG.debug("No User ID found in primary key with key ID {} containing one of the parts {}", primaryKey.getKeyID(),
                        useridParts);
                continue;
            }
            LOG.debug("User ID {} found in primary key with key ID {} containing one of the parts {}", new Object[] {
                foundKeyUserIdForUserIdPart[0], primaryKey.getKeyID(), useridParts });
            // add adequate keys to the result
            for (Iterator<PGPPublicKey> keyIter = keyRing.getPublicKeys(); keyIter.hasNext();) {
                PGPPublicKey key = keyIter.next();
                if (forEncryption) {
                    if (isEncryptionKey(key)) {
                        LOG.debug("Public encryption key with key user ID {} and key ID {} added to the encryption keys",
                                foundKeyUserIdForUserIdPart[0], Long.toString(key.getKeyID()));
                        result.add(key);
                    }
                } else if (!forEncryption && isSignatureKey(key)) {
                    // not used!
                    result.add(key);
                    LOG.debug("Public key with key user ID {} and key ID {} added to the signing keys", foundKeyUserIdForUserIdPart[0],
                            Long.toString(key.getKeyID()));
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
                    Long.toString(key.getKeyID()));
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
    private static String[] findFirstKeyUserIdContainingOneOfTheParts(List<String> useridParts, PGPPublicKey primaryKey) {
        String[] foundKeyUserIdForUserIdPart = null;
        for (@SuppressWarnings("unchecked")
        Iterator<String> iterator = primaryKey.getUserIDs(); iterator.hasNext();) {
            String keyUserId = iterator.next();
            for (String userIdPart : useridParts) {
                if (keyUserId.contains(userIdPart)) {
                    foundKeyUserIdForUserIdPart = new String[] {keyUserId, userIdPart };
                }
            }
        }
        return foundKeyUserIdForUserIdPart;
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
        PGPSecretKeyRingCollection pgpSec = 
            new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput),
                                           new BcKeyFingerprintCalculator());
        PGPObjectFactory factory = new PGPObjectFactory(PGPUtil.getDecoderStream(encryptedInput),
                                                        new BcKeyFingerprintCalculator());
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

    private static List<PGPSecretKeyAndPrivateKeyAndUserId> findSecretKeysWithPrivateKeyAndUserId(InputStream keyringInput,
            Map<String, String> sigKeyUserId2Password, String provider) throws IOException, PGPException, NoSuchProviderException {
        PGPSecretKeyRingCollection pgpSec = 
            new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput),
                                           new BcKeyFingerprintCalculator());
        return findSecretKeysWithPrivateKeyAndUserId(sigKeyUserId2Password, provider, pgpSec);
    }

    public static List<PGPSecretKeyAndPrivateKeyAndUserId> findSecretKeysWithPrivateKeyAndUserId(Map<String, String> sigKeyUserId2Password,
            String provider, PGPSecretKeyRingCollection pgpSec) throws PGPException {
        List<PGPSecretKeyAndPrivateKeyAndUserId> result = new ArrayList<PGPSecretKeyAndPrivateKeyAndUserId>(sigKeyUserId2Password.size());
        for (Iterator<?> i = pgpSec.getKeyRings(); i.hasNext();) {
            Object data = i.next();
            if (data instanceof PGPSecretKeyRing) {
                PGPSecretKeyRing keyring = (PGPSecretKeyRing) data;
                PGPSecretKey primaryKey = keyring.getSecretKey();
                List<String> useridParts = new ArrayList<String>(sigKeyUserId2Password.keySet());
                String[] foundKeyUserIdForUserIdPart = findFirstKeyUserIdContainingOneOfTheParts(useridParts, primaryKey.getPublicKey());
                if (foundKeyUserIdForUserIdPart == null) {
                    LOG.debug("No User ID found in primary key with key ID {} containing one of the parts {}", primaryKey.getKeyID(),
                            useridParts);
                    continue;
                }
                LOG.debug("User ID {} found in primary key with key ID {} containing one of the parts {}", new Object[] {
                    foundKeyUserIdForUserIdPart[0], primaryKey.getKeyID(), useridParts });
                // add all signing keys
                for (Iterator<PGPSecretKey> iterKey = keyring.getSecretKeys(); iterKey.hasNext();) {
                    PGPSecretKey secKey = iterKey.next();
                    if (isSigningKey(secKey)) {
                        PGPPrivateKey privateKey = secKey.extractPrivateKey(new JcePBESecretKeyDecryptorBuilder().setProvider(provider)
                                .build(sigKeyUserId2Password.get(foundKeyUserIdForUserIdPart[1]).toCharArray()));
                        if (privateKey != null) {
                            result.add(new PGPSecretKeyAndPrivateKeyAndUserId(secKey, privateKey, foundKeyUserIdForUserIdPart[0]));
                            LOG.debug("Private key with user ID {} and key ID {} added to the signing keys",
                                    foundKeyUserIdForUserIdPart[0], Long.toString(privateKey.getKeyID()));

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

    /**
     * Determines a public key from the keyring collection which has a certain
     * key ID and which has a User ID which contains at least one of the User ID
     * parts.
     * 
     * @param keyId
     *            key ID
     * @param userIdParts
     *            user ID parts, can be empty, than no filter on the User ID is
     *            executed
     * @param publicKeyringCollection
     *            keyring collection
     * @return public key or <code>null</code> if no fitting key is found
     * @throws PGPException
     */
    @SuppressWarnings("unchecked")
    public static PGPPublicKey getPublicKeyWithKeyIdAndUserID(long keyId, List<String> userIdParts, PGPPublicKeyRingCollection publicKeyringCollection)
        throws PGPException {
        PGPPublicKeyRing publicKeyring = publicKeyringCollection.getPublicKeyRing(keyId);
        if (publicKeyring == null) {
            LOG.debug("No public key found for key ID {}.", Long.toString(keyId));
            return null;
        }
        // publicKey can be a subkey the user IDs must therefore be provided by the primary/master key
        if (isAllowedKey(userIdParts, publicKeyring.getPublicKey().getUserIDs())) {
            return publicKeyring.getPublicKey(keyId);
        } else {
            return null;
        }
    }

    private static boolean isAllowedKey(List<String> allowedUserIds, Iterator<String> verifyingPublicKeyUserIds) {

        if (allowedUserIds == null || allowedUserIds.isEmpty()) {
            // no restrictions specified
            return true;
        }
        String keyUserId = null;
        for (; verifyingPublicKeyUserIds.hasNext();) {
            keyUserId = verifyingPublicKeyUserIds.next();
            for (String userid : allowedUserIds) {
                if (keyUserId != null && keyUserId.contains(userid)) {
                    LOG.debug(
                            "Public key with  user ID {} fulfills the User ID restriction.",
                            keyUserId, allowedUserIds);
                    return true;
                }
            }
        }
        LOG.warn(
                "Public key with User ID {} does not fulfill the User ID restriction.",
                keyUserId, allowedUserIds);
        return false;
    }

}
