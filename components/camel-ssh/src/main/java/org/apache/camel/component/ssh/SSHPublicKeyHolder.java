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
package org.apache.camel.component.ssh;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

public class SSHPublicKeyHolder {
    private static final String SSH_RSA = "ssh-rsa";
    private static final String SSH_DSS = "ssh-dss";
    private static final String SSH_ECDSA = "ecdsa-sha2-nistp256";
    private static final String SSH_ED25519 = "ssh-ed25519";

    private String keyType;

    /* RSA key parts */
    private BigInteger e;
    private BigInteger m;

    /* DSA key parts */
    private BigInteger p;
    private BigInteger q;
    private BigInteger g;
    private BigInteger y;

    public String getKeyType() {
        return keyType;
    }

    public void setKeyType(String keyType) {
        this.keyType = keyType;
    }

    public BigInteger getE() {
        return e;
    }

    public void setE(BigInteger e) {
        this.e = e;
    }

    public BigInteger getM() {
        return m;
    }

    public void setM(BigInteger m) {
        this.m = m;
    }

    public BigInteger getG() {
        return g;
    }

    public void setG(BigInteger g) {
        this.g = g;
    }

    public BigInteger getP() {
        return p;
    }

    public void setP(BigInteger p) {
        this.p = p;
    }

    public BigInteger getQ() {
        return q;
    }

    public void setQ(BigInteger q) {
        this.q = q;
    }

    public BigInteger getY() {
        return y;
    }

    public void setY(BigInteger y) {
        this.y = y;
    }

    public void push(byte[] keyPart) {
        if (keyType == null) {
            this.keyType = new String(keyPart, Charset.forName("UTF-8"));
            return;
        }

        if (SSH_RSA.equals(keyType)) {
            if (e == null) {
                this.e = new BigInteger(keyPart);
                return;
            }

            if (m == null) {
                this.m = new BigInteger(keyPart);
                return;
            }
        }

        if (SSH_DSS.equals(keyType)) {
            if (p == null) {
                this.p = new BigInteger(keyPart);
            }

            if (q == null) {
                this.q = new BigInteger(keyPart);
            }

            if (g == null) {
                this.g = new BigInteger(keyPart);
            }

            if (y == null) {
                this.y = new BigInteger(keyPart);
            }
        }

    }

    public PublicKey toPublicKey() throws NoSuchAlgorithmException, InvalidKeySpecException {
        PublicKey returnValue = null;

        if (SSH_RSA.equals(keyType)) {
            RSAPublicKeySpec dsaPublicKeySpec = new RSAPublicKeySpec(m, e);
            KeyFactory factory = KeyFactory.getInstance("RSA");
            returnValue = factory.generatePublic(dsaPublicKeySpec);
        }

        if (SSH_DSS.equals(keyType)) {
            DSAPublicKeySpec dsaPublicKeySpec = new DSAPublicKeySpec(y, p, q, g);
            KeyFactory factory = KeyFactory.getInstance("DSA");
            returnValue = factory.generatePublic(dsaPublicKeySpec);
        }

        if (SSH_ED25519.equals(keyType)) {
            throw new UnsupportedOperationException();
        }

        if (SSH_ECDSA.equals(keyType)) {
            throw new UnsupportedOperationException();
        }

        return returnValue;
    }
}