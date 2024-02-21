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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.DSAPublicKeySpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.RSAPublicKeySpec;

import org.apache.sshd.common.cipher.ECCurves;
import org.bouncycastle.jcajce.spec.OpenSSHPublicKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

public class SSHPublicKeyHolder {
    private static final String SSH_RSA = "ssh-rsa";
    private static final String SSH_DSS = "ssh-dss";

    private static final String SSH_ECDSA_PREFIX = "ecdsa-sha2-";
    // ssh-keygen ... -b 256 -t ecdsa
    private static final String SSH_ECDSA = SSH_ECDSA_PREFIX + "nistp256";
    // ssh-keygen ... -b 384 -t ecdsa
    private static final String SSH_ECDSA_384 = SSH_ECDSA_PREFIX + "nistp384";
    // ssh-keygen ... -b 521 -t ecdsa # yes - "521", not "512"
    private static final String SSH_ECDSA_521 = SSH_ECDSA_PREFIX + "nistp521";

    // ssh-keygen ... -t ed25519
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

    /* EC key parts */
    private String curveName;
    private ECPoint ecPoint;
    private ECParameterSpec ecParams;

    /* EdDSA key parts */
    private final ByteArrayOutputStream edKeyEncoded = new ByteArrayOutputStream();

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
            this.keyType = new String(keyPart, StandardCharsets.UTF_8);
            if (SSH_ED25519.equals(keyType)) {
                encode(edKeyEncoded, keyType);
            }
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
                return;
            }

            if (q == null) {
                this.q = new BigInteger(keyPart);
                return;
            }

            if (g == null) {
                this.g = new BigInteger(keyPart);
                return;
            }

            if (y == null) {
                this.y = new BigInteger(keyPart);
                return;
            }
        }

        if (keyType.equals(SSH_ED25519)) {
            // https://tools.ietf.org/html/rfc8709
            // https://tools.ietf.org/html/rfc8032#section-5.2.5
            encode(edKeyEncoded, keyPart);
            return;
        }

        if (keyType.startsWith(SSH_ECDSA_PREFIX)) {
            // https://tools.ietf.org/html/rfc5656#section-3.1
            // see org.apache.sshd.common.util.buffer.keys.ECBufferPublicKeyParser.getRawECKey
            if (curveName == null) {
                curveName = new String(keyPart, StandardCharsets.UTF_8);
                return;
            }
            if (ecPoint == null) {
                ecParams = ECCurves.fromKeyType(keyType).getParameters();
                ecPoint = ECCurves.octetStringToEcPoint(keyPart);
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
            OpenSSHPublicKeySpec ed25519PublicKeySpec = new OpenSSHPublicKeySpec(edKeyEncoded.toByteArray());
            KeyFactory factory = KeyFactory.getInstance("ED25519", new BouncyCastleProvider());
            returnValue = factory.generatePublic(ed25519PublicKeySpec);
        }

        if (keyType.startsWith(SSH_ECDSA_PREFIX)) {
            ECPublicKeySpec spec = new ECPublicKeySpec(ecPoint, ecParams);
            KeyFactory factory = KeyFactory.getInstance("EC");
            returnValue = factory.generatePublic(spec);
        }

        return returnValue;
    }

    private void encode(ByteArrayOutputStream target, byte[] value) {
        byte[] result = new byte[4 + value.length];
        result[0] = (byte) ((value.length & 0xFF) << 24);
        result[1] = (byte) ((value.length & 0xFF) << 16);
        result[2] = (byte) ((value.length & 0xFF) << 8);
        result[3] = (byte) (value.length & 0xFF);
        System.arraycopy(value, 0, result, 4, value.length);
        try {
            target.write(result);
        } catch (IOException ignored) {
        }
    }

    private void encode(ByteArrayOutputStream target, String v) {
        byte[] value = v.getBytes(StandardCharsets.UTF_8);
        encode(target, value);
    }
}
