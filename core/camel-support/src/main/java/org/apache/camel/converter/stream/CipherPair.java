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
package org.apache.camel.converter.stream;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.spec.IvParameterSpec;

/**
 * A class to hold a pair of encryption and decryption ciphers.
 */
public class CipherPair {
    private final String transformation;
    private final Cipher enccipher;
    private final Cipher deccipher;
    
    public CipherPair(String transformation) throws GeneralSecurityException {
        this.transformation = transformation;
        
        int d = transformation.indexOf('/');
        String a;
        if (d > 0) {
            a = transformation.substring(0, d);
        } else {
            a = transformation;
        }

        KeyGenerator keygen = KeyGenerator.getInstance(a);
        keygen.init(new SecureRandom());
        Key key = keygen.generateKey();
        enccipher = Cipher.getInstance(transformation);
        deccipher = Cipher.getInstance(transformation);
        enccipher.init(Cipher.ENCRYPT_MODE, key);
        final byte[] ivp = enccipher.getIV();
        deccipher.init(Cipher.DECRYPT_MODE, key, ivp == null ? null : new IvParameterSpec(ivp));
    }
    
    public String getTransformation() {
        return transformation;
    }
    
    public Cipher getEncryptor() {
        return enccipher;
    }
    
    public Cipher getDecryptor() {
        return deccipher;
    }
}
