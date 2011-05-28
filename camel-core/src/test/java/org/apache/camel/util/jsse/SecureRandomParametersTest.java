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
import java.security.SecureRandom;

import org.apache.camel.CamelContext;

public class SecureRandomParametersTest extends AbstractJsseParametersTest {
    
    public void testPropertyPlaceholders() throws Exception {
        if (canTest()) {
            CamelContext context = this.createPropertiesPlaceholderAwareContext();
            
            SecureRandomParameters srp = new SecureRandomParameters();
            srp.setCamelContext(context);
            
            srp.setAlgorithm("{{secureRandomParameters.algorithm}}");
            srp.setProvider("{{secureRandomParameters.provider}}");
            
            srp.createSecureRandom();
        }
    }
    
    public void testCreateSecureRandom() throws Exception {
        
        if (this.canTest()) {
            SecureRandomParameters srp = new SecureRandomParameters();
            srp.setAlgorithm("SHA1PRNG");
            
            SecureRandom sr = srp.createSecureRandom();
            assertEquals("SHA1PRNG", sr.getAlgorithm());
            
            String providerName = sr.getProvider().getName();
            srp.setProvider(providerName);
            
            sr = srp.createSecureRandom();
            assertEquals("SHA1PRNG", sr.getAlgorithm());
            assertEquals(providerName, sr.getProvider().getName());
        }
    }
    
    public void testExplicitInvalidAlgorithm() throws Exception {
        SecureRandomParameters srp = new SecureRandomParameters();
        srp.setAlgorithm("fsafsadfasdfasdf");
        
        try {
            srp.createSecureRandom();
            fail();
        } catch (NoSuchAlgorithmException e) {
            // expected
        }
    }
    
    public void testExplicitInvalidProvider() throws Exception {
        if (this.canTest()) {
            SecureRandomParameters srp = new SecureRandomParameters();
            srp.setAlgorithm("SHA1PRNG");
            srp.setProvider("asdfsadfasdfasdf");
            
            try {
                srp.createSecureRandom();
                fail();
            } catch (NoSuchProviderException e) {
                // expected
            }
        }
    }
    
    protected boolean canTest() {
        try {
            SecureRandom.getInstance("SHA1PRNG");
            return true;
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }
}
