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
import java.security.interfaces.DSAParams;
import java.security.interfaces.DSAPublicKey;

import com.jcraft.jsch.Identity;
import com.jcraft.jsch.JSchException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DSAKeyPairIdentity implements Identity {
    private static final String ALGORITHM_TYPE = "ssh-dss";
    private final Logger log = LoggerFactory.getLogger(getClass());
    private KeyPair keyPair;
    private String name;

    public DSAKeyPairIdentity(String name, KeyPair keyPair) {
        this.name = name;
        this.keyPair = keyPair;
    }

    @Override
    public boolean setPassphrase(byte[] passphrase) throws JSchException {
        return true;
    }

    @Override
    public byte[] getPublicKeyBlob() {
        DSAPublicKey publicKey = (DSAPublicKey) keyPair.getPublic();
        byte[] sshDss = ALGORITHM_TYPE.getBytes();

        DSAParams dsaParams = publicKey.getParams();
        byte[] pArray = dsaParams.getP().toByteArray();
        byte[] qArray = dsaParams.getQ().toByteArray();
        byte[] gArray = dsaParams.getG().toByteArray();
        byte[] yArray = publicKey.getY().toByteArray();

        byte[] result = new byte[sshDss.length + 4 + pArray.length + 4 + qArray.length + 4 + gArray.length + 4 + yArray.length + 4];
        int index = 0;

        byte[] intAsByteArray = ByteBuffer.allocate(4).putInt(sshDss.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(sshDss, 0, result, index, sshDss.length);
        index += sshDss.length;

        intAsByteArray = ByteBuffer.allocate(4).putInt(pArray.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(pArray, 0, result, index, pArray.length);
        index += pArray.length;

        intAsByteArray = ByteBuffer.allocate(4).putInt(qArray.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(qArray, 0, result, index, qArray.length);
        index += qArray.length;

        intAsByteArray = ByteBuffer.allocate(4).putInt(gArray.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(gArray, 0, result, index, gArray.length);
        index += gArray.length;

        intAsByteArray = ByteBuffer.allocate(4).putInt(yArray.length).array();
        System.arraycopy(intAsByteArray, 0, result, index, 4);
        index += 4;

        System.arraycopy(yArray, 0, result, index, yArray.length);

        return result;
    }

    @Override
    public byte[] getSignature(byte[] data) {
        try {
            PrivateKey prvKey = keyPair.getPrivate();
            Signature sig;
            sig = Signature.getInstance("SHA1withDSA");
            sig.initSign(prvKey);
            sig.update(data);
            byte[] sshDss = ALGORITHM_TYPE.getBytes();
            byte[] signature = sig.sign();

            signature = convertDERToP1363(signature);

            byte[] result = new byte[sshDss.length + 4 + signature.length + 4];
            int index = 0;

            byte[] intAsByteArray = ByteBuffer.allocate(4).putInt(sshDss.length).array();
            System.arraycopy(intAsByteArray, 0, result, index, 4);
            index += 4;

            System.arraycopy(sshDss, 0, result, index, sshDss.length);
            index += sshDss.length;

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

    /**
     * "The signature on m is (r, s). Message m and (r, s) should be sent to the
     * verifier. We need to observe that both r and s are 20 bytes, since a
     * modular reduction is being performed (steps 2 and 5) using q, a 160 bit
     * value. This will gain significance later when we begin verifying messages
     * between Crypto++ and C# (which use the IEEE P1363 signature format) and
     * Java (which uses a DER encoding of a signature)." [taken from <a href=
     * "http://www.codeproject.com/Articles/25590/Cryptographic-Interoperability-Digital-Signatures"
     * >Cryptographic Interoperability Digital Signatures</a>]
     * 
     * @param sig
     *            signature in DER format
     * @return signature in P1363 format; SEQUENCE ::= { r INTEGER, s INTEGER }
     */
    private byte[] convertDERToP1363(byte[] sig) {
        int index = 3;
        int len = sig[index++] & 0xff;

        byte[] r = new byte[len];
        System.arraycopy(sig, index, r, 0, r.length);
        index = index + len + 1;

        len = sig[index++] & 0xff;
        byte[] s = new byte[len];
        System.arraycopy(sig, index, s, 0, s.length);

        byte[] p1363Signature = new byte[40];
        if (r.length > 21 || (r.length == 21 && r[0] != 0)) {
            // Reject - signature verification failed
        } else if (r.length == 21) {
            // r[0] = 0
            // r[1]'s high bit *should* be set
            System.arraycopy(r, 1, p1363Signature, 0, 20);
        } else if (r.length == 20) {
            // r[0]'s high bit *should not* be set
            System.arraycopy(r, 0, p1363Signature, 0, 20);
        } else {
            // fewer than 20 bytes
            len = r.length;
            int off = 20 - len;
            System.arraycopy(r, 0, p1363Signature, off, len);
        }

        if (s.length > 21 || (s.length == 21 && s[0] != 0)) {
            // Reject - signature verification failed
            p1363Signature = new byte[0];
        } else if (s.length == 21) {
            // s[0] = 0
            // s[1]'s high bit *should* be set
            System.arraycopy(s, 1, p1363Signature, 20, 20);
        } else if (s.length == 20) {
            // s[0]'s high bit *should not* be set
            System.arraycopy(s, 0, p1363Signature, 20, 20);
        } else {
            // fewer than 20 bytes
            len = s.length;
            int off = 40 - len;
            System.arraycopy(s, 0, p1363Signature, off, len);
        }

        return p1363Signature;
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
