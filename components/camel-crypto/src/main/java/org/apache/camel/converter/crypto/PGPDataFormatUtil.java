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

import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.util.Iterator;

import org.apache.camel.CamelContext;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ResourceHelper;
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
import org.bouncycastle.openpgp.PGPUtil;

import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.DSA;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.ECDSA;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.ELGAMAL_GENERAL;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.RSA_GENERAL;
import static org.bouncycastle.bcpg.PublicKeyAlgorithmTags.RSA_SIGN;

public final class PGPDataFormatUtil {

    private PGPDataFormatUtil() {
    }

    public static PGPPublicKey findPublicKey(CamelContext context, String filename, String userid, boolean forEncryption) throws IOException, PGPException,
            NoSuchProviderException {

        InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), filename);
        PGPPublicKey pubKey;
        try {
            pubKey = findPublicKey(is, userid, forEncryption);
        } finally {
            IOHelper.close(is);
        }
        return pubKey;
    }

    @SuppressWarnings("unchecked")
    private static PGPPublicKey findPublicKey(InputStream input, String userid, boolean forEncryption) throws IOException, PGPException,
            NoSuchProviderException {
        PGPPublicKeyRingCollection pgpSec = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(input));

        Iterator<PGPPublicKeyRing> keyRingIter = pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPPublicKeyRing keyRing = keyRingIter.next();

            Iterator<PGPPublicKey> keyIter = keyRing.getPublicKeys();
            String keyUserId = null;
            while (keyIter.hasNext()) {
                PGPPublicKey key = keyIter.next();
                for (Iterator<String> iterator = key.getUserIDs(); iterator.hasNext();) {
                    keyUserId = iterator.next();
                }
                if (keyUserId != null && keyUserId.contains(userid)) {
                    if (forEncryption && key.isEncryptionKey()) {
                        return key;
                    } else if (!forEncryption && isSignatureKey(key)) {
                        return key;
                    }
                }
            }
        }

        return null;
    }

    private static boolean isSignatureKey(PGPPublicKey key) {
        int algorithm = key.getAlgorithm();
        return algorithm == RSA_GENERAL || algorithm == RSA_SIGN || algorithm == DSA || algorithm == ECDSA || algorithm == ELGAMAL_GENERAL;
    }

    public static PGPPrivateKey findPrivateKey(CamelContext context, String keychainFilename, InputStream encryptedInput, String passphrase)
        throws IOException, PGPException, NoSuchProviderException {

        InputStream keyChainInputStream = ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), keychainFilename);

        PGPPrivateKey privKey = null;
        try {
            privKey = findPrivateKey(keyChainInputStream, encryptedInput, passphrase);
        } finally {
            IOHelper.close(keyChainInputStream);
        }
        return privKey;
    }

    private static PGPPrivateKey findPrivateKey(InputStream keyringInput, InputStream encryptedInput, String passphrase) throws IOException,
            PGPException, NoSuchProviderException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput));
        PGPObjectFactory factory = new PGPObjectFactory(PGPUtil.getDecoderStream(encryptedInput));
        PGPEncryptedDataList enc;
        Object o = factory.nextObject();
        if (o instanceof PGPEncryptedDataList) {
            enc = (PGPEncryptedDataList) o;
        } else {
            enc = (PGPEncryptedDataList) factory.nextObject();
        }
        encryptedInput.reset(); // nextObject() method reads from the InputStream, so rewind it!
        Iterator<?> encryptedDataObjects = enc.getEncryptedDataObjects();
        PGPPrivateKey privateKey = null;
        PGPPublicKeyEncryptedData encryptedData;
        while (privateKey == null && encryptedDataObjects.hasNext()) {
            encryptedData = (PGPPublicKeyEncryptedData) encryptedDataObjects.next();
            PGPSecretKey pgpSecKey = pgpSec.getSecretKey(encryptedData.getKeyID());
            privateKey = pgpSecKey.extractPrivateKey(passphrase.toCharArray(), "BC");
        }
        return privateKey;
    }

    public static PGPSecretKey findSecretKey(CamelContext context, String keychainFilename, String passphrase)
        throws IOException, PGPException, NoSuchProviderException {

        InputStream keyChainInputStream = ResourceHelper.resolveMandatoryResourceAsInputStream(context.getClassResolver(), keychainFilename);

        PGPSecretKey secKey = null;
        try {
            secKey = findSecretKey(keyChainInputStream, passphrase);
        } finally {
            IOHelper.close(keyChainInputStream);
        }
        return secKey;
    }

    private static PGPSecretKey findSecretKey(InputStream keyringInput, String passphrase) throws IOException, PGPException, NoSuchProviderException {
        PGPSecretKey pgpSecKey = null;
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(keyringInput));
        for (Iterator<?> i = pgpSec.getKeyRings(); i.hasNext() && pgpSecKey == null;) {
            Object data = i.next();
            if (data instanceof PGPSecretKeyRing) {
                PGPSecretKeyRing keyring = (PGPSecretKeyRing) data;
                PGPSecretKey secKey = keyring.getSecretKey();
                PGPPrivateKey privateKey = secKey.extractPrivateKey(passphrase.toCharArray(), "BC");
                if (privateKey != null) {
                    pgpSecKey = secKey;
                }
            }
        }
        return pgpSecKey;
    }
}
