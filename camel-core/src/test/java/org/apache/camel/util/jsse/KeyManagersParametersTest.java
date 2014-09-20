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
package org.apache.camel.util.jsse;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.X509KeyManager;

import org.apache.camel.CamelContext;

public class KeyManagersParametersTest extends AbstractJsseParametersTest {
    
    protected KeyStoreParameters createMinimalKeyStoreParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        
        ksp.setResource("org/apache/camel/util/jsse/localhost.ks");
        ksp.setPassword("changeit");
        
        return ksp;
    }
    
    protected KeyManagersParameters createMinimalKeyManagersParameters() {
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setKeyStore(this.createMinimalKeyStoreParameters());
        kmp.setKeyPassword("changeit");
        
        return kmp;
    }
    
    public void testPropertyPlaceholders() throws Exception {
        
        CamelContext context = this.createPropertiesPlaceholderAwareContext();
        
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setCamelContext(context);
        
        ksp.setType("{{keyStoreParameters.type}}");
        ksp.setProvider("{{keyStoreParameters.provider}}");
        ksp.setResource("{{keyStoreParameters.resource}}");
        ksp.setPassword("{{keyStoreParamerers.password}}");
        
        KeyManagersParameters kmp = new KeyManagersParameters();
        kmp.setCamelContext(context);
        kmp.setKeyStore(ksp);
        
        kmp.setKeyPassword("{{keyManagersParameters.keyPassword}}");
        kmp.setAlgorithm("{{keyManagersParameters.algorithm}}");
        kmp.setProvider("{{keyManagersParameters.provider}}");
        
        KeyManager[] kms = kmp.createKeyManagers();
        validateKeyManagers(kms);
    }
    
    public void testCreateKeyManagers() throws Exception {
        KeyManagersParameters kmp = this.createMinimalKeyManagersParameters();
        
        KeyManager[] kms = kmp.createKeyManagers();
        validateKeyManagers(kms);
    }
    
    public void testExplicitAlgorithm() throws Exception {
        KeyManagersParameters kmp = this.createMinimalKeyManagersParameters();
        kmp.setAlgorithm(KeyManagerFactory.getDefaultAlgorithm());
        
        KeyManager[] kms = kmp.createKeyManagers();
        validateKeyManagers(kms);
    }
    
    public void testExplicitProvider() throws Exception {
        KeyManagersParameters kmp = this.createMinimalKeyManagersParameters();
        kmp.setProvider(KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
                        .getProvider().getName());
        
        KeyManager[] kms = kmp.createKeyManagers();
        validateKeyManagers(kms);
    }
    
    public void testInvalidPassword() throws Exception {
        KeyManagersParameters kmp = this.createMinimalKeyManagersParameters();
        kmp.setKeyPassword("");
        
        try {
            kmp.createKeyManagers();
            fail();
        } catch (UnrecoverableKeyException e) {
            // expected
        }
    }
    
    public void testInvalidExplicitAlgorithm() throws Exception {
        KeyManagersParameters kmp = this.createMinimalKeyManagersParameters();
        kmp.setAlgorithm("dsfsdfsdfdsfdsF");
        
        try {
            kmp.createKeyManagers();
            fail();
        } catch (NoSuchAlgorithmException e) {
            // expected
        }
    }
    
    public void testInvalidExplicitProvider() throws Exception {
        KeyManagersParameters kmp = this.createMinimalKeyManagersParameters();
        kmp.setProvider("dsfsdfsdfdsfdsF");
        
        try {
            kmp.createKeyManagers();
            fail();
        } catch (NoSuchProviderException e) {
            // expected
        }
    }
    
    public void testAliasedKeyManager() throws Exception {
        KeyManagersParameters kmp = this.createMinimalKeyManagersParameters();
        
        KeyManager[] kms = kmp.createKeyManagers();
        assertEquals(1, kms.length);
        assertTrue(kms[0] instanceof X509KeyManager);
        
        kms[0] = new AliasedX509ExtendedKeyManager("server", (X509KeyManager)kms[0]);
        AliasedX509ExtendedKeyManager km = (AliasedX509ExtendedKeyManager) kms[0];
        assertNotNull(km.getPrivateKey("server"));
    }

    protected void validateKeyManagers(KeyManager[] kms) {
        assertEquals(1, kms.length);
        assertTrue(kms[0] instanceof X509KeyManager);
        X509KeyManager km = (X509KeyManager) kms[0];
        assertNotNull(km.getPrivateKey("server"));
    }
}
