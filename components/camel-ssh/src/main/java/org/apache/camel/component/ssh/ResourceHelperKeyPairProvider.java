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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.apache.camel.CamelContext;
import org.apache.camel.support.ResourceHelper;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.util.EC5Util;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JceOpenSSLPKCS8DecryptorProviderBuilder;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
import org.bouncycastle.operator.InputDecryptorProvider;
import org.bouncycastle.pkcs.PKCS8EncryptedPrivateKeyInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This host key provider loads private keys from the specified resources using
 * {@link ResourceHelper}, and Camel's resource syntax for file:, classpath:, and http:.
 * {@link ResourceHelper}, and Camel's resource syntax for file:, classpath:, and http:.
 *
 * Note that this class has a direct dependency on BouncyCastle and won't work
 * unless it has been correctly registered as a security provider.
 */
public class ResourceHelperKeyPairProvider extends AbstractKeyPairProvider {
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private CamelContext camelContext;
    private String[] resources;
    private Supplier<char[]> passwordFinder;

    public ResourceHelperKeyPairProvider() {
    }

    public ResourceHelperKeyPairProvider(String[] resources,
                                         CamelContext camelContext) {
        this.camelContext = camelContext;
        this.resources = resources;
    }

    public ResourceHelperKeyPairProvider(String[] resources,
                                         Supplier<char[]> passwordFinder,
                                         CamelContext camelContext) {
        this.camelContext = camelContext;
        this.resources = resources;
        this.passwordFinder = passwordFinder;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public String[] getResources() {
        return resources;
    }

    public void setResources(String[] resources) {
        this.resources = resources;
    }

    public Supplier<char[]> getPasswordFinder() {
        return passwordFinder;
    }

    public void setPasswordFinder(Supplier<char[]> passwordFinder) {
        this.passwordFinder = passwordFinder;
    }

    @Override
    public Iterable<KeyPair> loadKeys() {
        if (!SecurityUtils.isBouncyCastleRegistered()) {
            throw new IllegalStateException("BouncyCastle must be registered as a JCE provider");
        }

        final List<KeyPair> keys =
                new ArrayList<>(this.resources.length);

        for (String resource : resources) {
            PEMParser r = null;
            InputStreamReader isr = null;
            InputStream is = null;
            try {
                is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, resource);
                isr = new InputStreamReader(is);
                r = new PEMParser(isr);

                Object o = r.readObject();

                JcaPEMKeyConverter pemConverter = new JcaPEMKeyConverter();
                pemConverter.setProvider("BC");
                if (passwordFinder != null && o instanceof PEMEncryptedKeyPair) {
                    JcePEMDecryptorProviderBuilder decryptorBuilder = new JcePEMDecryptorProviderBuilder();
                    PEMDecryptorProvider pemDecryptor = decryptorBuilder.build(passwordFinder.get());
                    o = pemConverter.getKeyPair(((PEMEncryptedKeyPair) o).decryptKeyPair(pemDecryptor));
                } else if (passwordFinder != null && o instanceof PKCS8EncryptedPrivateKeyInfo) {
                    JceOpenSSLPKCS8DecryptorProviderBuilder jce = new JceOpenSSLPKCS8DecryptorProviderBuilder();
                    jce.setProvider("BC");
                    InputDecryptorProvider decProv = jce.build(passwordFinder.get());
                    o = ((PKCS8EncryptedPrivateKeyInfo)o).decryptPrivateKeyInfo(decProv);
                }

                if (o instanceof PEMKeyPair) {
                    o = pemConverter.getKeyPair((PEMKeyPair)o);
                    keys.add((KeyPair) o);
                } else if (o instanceof KeyPair) {
                    keys.add((KeyPair) o);
                } else if (o instanceof PrivateKeyInfo) {
                    PrivateKey privateKey = pemConverter.getPrivateKey((PrivateKeyInfo)o);
                    PublicKey publicKey = convertPrivateToPublicKey(privateKey);
                    if (publicKey != null) {
                        keys.add(new KeyPair(publicKey, privateKey));
                    }
                }

            } catch (Exception e) {
                log.warn("Unable to read key", e);
            } finally {
                IoUtils.closeQuietly(r, is, isr);
            }
        }

        return keys;
    }

    private PublicKey convertPrivateToPublicKey(PrivateKey privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (privateKey instanceof RSAPrivateCrtKey) {
            KeySpec keySpec = new RSAPublicKeySpec(((RSAPrivateCrtKey)privateKey).getModulus(),
                                                            ((RSAPrivateCrtKey)privateKey).getPublicExponent());
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            return keyFactory.generatePublic(keySpec);
        } else if (privateKey instanceof ECPrivateKey) {
            ECPrivateKey ecPrivateKey = (ECPrivateKey)privateKey;

            // Derive the public point by multiplying the generator by the private value
            ECParameterSpec paramSpec = EC5Util.convertSpec(ecPrivateKey.getParams());
            ECPoint q = paramSpec.getG().multiply(ecPrivateKey.getS());

            KeySpec keySpec = new ECPublicKeySpec(q, paramSpec);

            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            return keyFactory.generatePublic(keySpec);
        } else {
            log.warn("Unable to convert private key to public key. Only RSA + ECDSA supported");
            return null;
        }
    }

}
