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
package org.apache.camel.converter.stream;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;

import org.apache.camel.util.StringHelper;

/**
 * A class to hold a pair of encryption and decryption ciphers.
 */
public class CipherPair {
    private final String transformation;
    private final Cipher enccipher;
    private final Key key;
    private final byte[] ivp;

    public CipherPair(String transformation) throws GeneralSecurityException {
        this.transformation = transformation;

        String a = StringHelper.before(transformation, "/", transformation);

        KeyGenerator keygen = KeyGenerator.getInstance(a);
        keygen.init(new SecureRandom());
        key = keygen.generateKey();
        enccipher = Cipher.getInstance(transformation);
        enccipher.init(Cipher.ENCRYPT_MODE, key);
        ivp = enccipher.getIV();
    }

    public String getTransformation() {
        return transformation;
    }

    public Cipher getEncryptor() {
        return enccipher;
    }

    /**
     * Create the decryptor every time because the decryptor is not thead safe. For example, if you reuse the decryptor
     * instance in the Multi-cast case then you will get errors.
     */
    public Cipher createDecryptor() {
        try {
            Cipher deccipher = Cipher.getInstance(transformation);
            deccipher.init(Cipher.DECRYPT_MODE, key, ivp == null ? null : new IvParameterSpec(ivp));
            return deccipher;
        } catch (GeneralSecurityException e) {
            // should not happen
            throw new IllegalStateException("Could not instanciate decryptor", e);
        }
    }
}
