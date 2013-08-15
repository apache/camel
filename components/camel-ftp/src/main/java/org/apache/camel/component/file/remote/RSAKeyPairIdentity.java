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
package org.apache.camel.component.file.remote;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.interfaces.RSAPublicKey;

import com.jcraft.jsch.Identity;
import com.jcraft.jsch.JSchException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



public class RSAKeyPairIdentity implements Identity {
    private static final String ALGORITHM_TYPE = "ssh-rsa";
    private final Logger log = LoggerFactory.getLogger(getClass());
    
    private KeyPair keyPair;
    private String name;

    public RSAKeyPairIdentity(String name, KeyPair keyPair) {
        this.name = name;
        this.keyPair = keyPair;
    }

    @Override
    public boolean setPassphrase(byte[] passphrase) throws JSchException {
        return true;
    }

    @Override
    public byte[] getPublicKeyBlob() {
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        byte[] sshRsa = ALGORITHM_TYPE.getBytes();
        byte[] eArray = publicKey.getPublicExponent().toByteArray();
        byte[] nArray = publicKey.getModulus().toByteArray();

        byte[] result = new byte[sshRsa.length + 4 + eArray.length + 4 + nArray.length + 4];
        int index = 0;

        byte[] intAsByteArray = ByteBuffer.allocate(4).putInt(sshRsa.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(sshRsa, 0, result, index, sshRsa.length);
        index += sshRsa.length;

        intAsByteArray = ByteBuffer.allocate(4).putInt(eArray.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(eArray, 0, result, index, eArray.length);
        index += eArray.length;

        intAsByteArray = ByteBuffer.allocate(4).putInt(nArray.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(nArray, 0, result, index, nArray.length);

        return result;
    }

    @Override
    public byte[] getSignature(byte[] data) {
        PrivateKey prvKey = keyPair.getPrivate();
        Signature sig;
        try {
            sig = Signature.getInstance("SHA1withRSA");
            sig.initSign(prvKey);
            sig.update(data);
            byte[] sshRsa = ALGORITHM_TYPE.getBytes();
            byte[] signature = sig.sign();
            byte[] result = new byte[sshRsa.length + 4 + signature.length + 4];
            int index = 0;

            byte[] intAsByteArray = ByteBuffer.allocate(4).putInt(sshRsa.length).array();
            System.arraycopy(intAsByteArray, 0, result, index, 4);
            index += 4;

            System.arraycopy(sshRsa, 0, result, index, sshRsa.length);
            index += sshRsa.length;

            intAsByteArray = ByteBuffer.allocate(4).putInt(signature.length).array();
            System.arraycopy(intAsByteArray, 0, result, index, 4);
            index += 4;

            System.arraycopy(signature, 0, result, index, signature.length);

            return result;
        } catch (NoSuchAlgorithmException e) {
            log.error("Cannot sign", e);
        } catch (InvalidKeyException e) {
            log.error("Cannot sign", e);
        } catch (SignatureException e) {
            log.error("Cannot sign", e);
        }
        return null;
    }

    @Override
    public boolean decrypt() {
        return true;
    }

    @Override
    public String getAlgName() {
        return ALGORITHM_TYPE;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public boolean isEncrypted() {
        return false;
    }

    @Override
    public void clear() {
    }
}
