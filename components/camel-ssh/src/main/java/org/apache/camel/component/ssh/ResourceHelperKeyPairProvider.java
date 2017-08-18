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

package org.apache.camel.component.ssh;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.ResourceHelper;
import org.apache.sshd.common.keyprovider.AbstractKeyPairProvider;
import org.apache.sshd.common.util.io.IoUtils;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.bouncycastle.openssl.PEMDecryptorProvider;
import org.bouncycastle.openssl.PEMEncryptedKeyPair;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.PasswordFinder;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.openssl.jcajce.JcePEMDecryptorProviderBuilder;
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

    private ClassResolver classResolver;
    private String[] resources;
    private PasswordFinder passwordFinder;

    public ResourceHelperKeyPairProvider() {
    }

    public ResourceHelperKeyPairProvider(String[] resources,
                                         ClassResolver classResolver) {
        this.classResolver = classResolver;
        this.resources = resources;
    }

    public ResourceHelperKeyPairProvider(String[] resources,
                                         PasswordFinder passwordFinder,
                                         ClassResolver classResolver) {
        this.classResolver = classResolver;
        this.resources = resources;
        this.passwordFinder = passwordFinder;
    }

    public ClassResolver getClassResolver() {
        return classResolver;
    }

    public void setClassResolver(ClassResolver classResolver) {
        this.classResolver = classResolver;
    }

    public String[] getResources() {
        return resources;
    }

    public void setResources(String[] resources) {
        this.resources = resources;
    }

    public PasswordFinder getPasswordFinder() {
        return passwordFinder;
    }

    public void setPasswordFinder(PasswordFinder passwordFinder) {
        this.passwordFinder = passwordFinder;
    }

    @Override
    public Iterable<KeyPair> loadKeys() {
        if (!SecurityUtils.isBouncyCastleRegistered()) {
            throw new IllegalStateException("BouncyCastle must be registered as a JCE provider");
        }

        final List<KeyPair> keys =
                new ArrayList<KeyPair>(this.resources.length);

        for (String resource : resources) {
            PEMParser r = null;
            InputStreamReader isr = null;
            InputStream is = null;
            try {
                is = ResourceHelper.resolveMandatoryResourceAsInputStream(classResolver, resource);
                isr = new InputStreamReader(is);
                r = new PEMParser(isr);

                Object o = r.readObject();
                
                JcaPEMKeyConverter pemConverter = new JcaPEMKeyConverter();
                pemConverter.setProvider("BC");
                if (passwordFinder != null && o instanceof PEMEncryptedKeyPair) {
                    JcePEMDecryptorProviderBuilder decryptorBuilder = new JcePEMDecryptorProviderBuilder();
                    PEMDecryptorProvider pemDecryptor = decryptorBuilder.build(passwordFinder.getPassword());
                    o = pemConverter.getKeyPair(((PEMEncryptedKeyPair) o).decryptKeyPair(pemDecryptor));
                }
                
                if (o instanceof PEMKeyPair) {
                    o = pemConverter.getKeyPair((PEMKeyPair)o);
                    keys.add((KeyPair) o);
                } else if (o instanceof KeyPair) {
                    keys.add((KeyPair) o);
                }
                
            } catch (Exception e) {
                log.warn("Unable to read key", e);
            } finally {
                IoUtils.closeQuietly(r, is, isr);
            }
        }

        return keys;
    }
    
}
