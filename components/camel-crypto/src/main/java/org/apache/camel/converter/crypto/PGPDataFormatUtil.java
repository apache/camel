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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.NoSuchProviderException;
import java.util.Iterator;

import org.apache.camel.util.IOHelper;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPrivateKey;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPPublicKeyRing;
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.bouncycastle.openpgp.PGPSecretKeyRing;
import org.bouncycastle.openpgp.PGPSecretKeyRingCollection;
import org.bouncycastle.openpgp.PGPUtil;

public final class PGPDataFormatUtil {

    private PGPDataFormatUtil() {
    }

    public static PGPPublicKey findPublicKey(String filename, String userid) throws IOException, PGPException,
            NoSuchProviderException {
        FileInputStream fis = new FileInputStream(filename);
        PGPPublicKey privKey;
        try {
            privKey = findPublicKey(fis, userid);
        } finally {
            IOHelper.close(fis);
        }
        return privKey;
    }

    @SuppressWarnings("unchecked")
    public static PGPPublicKey findPublicKey(InputStream input, String userid) throws IOException, PGPException,
            NoSuchProviderException {
        PGPPublicKeyRingCollection pgpSec = new PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(input));

        Iterator<PGPPublicKeyRing> keyRingIter = (Iterator<PGPPublicKeyRing>) pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPPublicKeyRing keyRing = keyRingIter.next();

            Iterator<PGPPublicKey> keyIter = (Iterator<PGPPublicKey>) keyRing.getPublicKeys();
            String keyUserId = null;
            while (keyIter.hasNext()) {
                PGPPublicKey key = keyIter.next();
                for (Iterator<String> iterator = (Iterator<String>) key.getUserIDs(); iterator.hasNext();) {
                    keyUserId = iterator.next();
                }
                if (key.isEncryptionKey() && keyUserId != null && keyUserId.contains(userid)) {
                    return key;
                }
            }
        }

        return null;
    }

    public static PGPPrivateKey findPrivateKey(String filename, String userid, String passphrase) throws IOException,
            PGPException, NoSuchProviderException {
        FileInputStream fis = new FileInputStream(filename);
        PGPPrivateKey privKey;
        try {
            privKey = findPrivateKey(fis, userid, passphrase);
        } finally {
            IOHelper.close(fis);
        }
        return privKey;
    }

    @SuppressWarnings("unchecked")
    public static PGPPrivateKey findPrivateKey(InputStream input, String userid, String passphrase) throws IOException,
            PGPException, NoSuchProviderException {
        PGPSecretKeyRingCollection pgpSec = new PGPSecretKeyRingCollection(PGPUtil.getDecoderStream(input));

        Iterator<PGPSecretKeyRing> keyRingIter = (Iterator<PGPSecretKeyRing>) pgpSec.getKeyRings();
        while (keyRingIter.hasNext()) {
            PGPSecretKeyRing keyRing = keyRingIter.next();

            Iterator<PGPSecretKey> keyIter = (Iterator<PGPSecretKey>) keyRing.getSecretKeys();
            while (keyIter.hasNext()) {
                PGPSecretKey key = keyIter.next();
                for (Iterator<String> iterator = (Iterator<String>) key.getUserIDs(); iterator.hasNext();) {
                    String userId = iterator.next();
                    if (key.isSigningKey() && userId.contains(userid)) {
                        return key.extractPrivateKey(passphrase.toCharArray(), "BC");
                    }
                }
            }
        }

        return null;
    }

}
