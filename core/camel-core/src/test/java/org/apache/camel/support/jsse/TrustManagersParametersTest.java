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
package org.apache.camel.support.jsse;

import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.*;

@DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on Github CI")
public class TrustManagersParametersTest extends AbstractJsseParametersTest {

    protected KeyStoreParameters createMinimalKeyStoreParameters() {
        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setCamelContext(new DefaultCamelContext());

        ksp.setResource("org/apache/camel/support/jsse/localhost.p12");
        ksp.setPassword("changeit");

        return ksp;
    }

    protected TrustManagersParameters createMinimalTrustManagersParameters() {
        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setCamelContext(new DefaultCamelContext());
        tmp.setKeyStore(this.createMinimalKeyStoreParameters());
        return tmp;
    }

    @Test
    public void testPropertyPlaceholders() throws Exception {
        CamelContext context = this.createPropertiesPlaceholderAwareContext();

        KeyStoreParameters ksp = new KeyStoreParameters();
        ksp.setCamelContext(context);

        ksp.setType("{{keyStoreParameters.type}}");
        ksp.setProvider("{{keyStoreParameters.provider}}");
        ksp.setResource("{{keyStoreParameters.resource}}");
        ksp.setPassword("{{keyStoreParamerers.password}}");

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setCamelContext(context);
        tmp.setKeyStore(ksp);

        tmp.setAlgorithm("{{trustManagersParameters.algorithm}}");
        tmp.setProvider("{{trustManagersParameters.provider}}");

        TrustManager[] tms = tmp.createTrustManagers();
        validateTrustManagers(tms);
    }

    @Test
    public void testCustomTrustManager() throws Exception {
        TrustManager myTm = new TrustManager() {
            // noop
        };

        TrustManagersParameters tmp = new TrustManagersParameters();
        tmp.setTrustManager(myTm);

        TrustManager[] tms = tmp.createTrustManagers();
        assertSame(myTm, tms[0]);
    }

    @Test
    public void testCreateTrustManagers() throws Exception {
        TrustManagersParameters tmp = this.createMinimalTrustManagersParameters();

        TrustManager[] tms = tmp.createTrustManagers();
        validateTrustManagers(tms);
    }

    @Test
    public void testExplicitAlgorithm() throws Exception {
        TrustManagersParameters tmp = this.createMinimalTrustManagersParameters();
        tmp.setAlgorithm(KeyManagerFactory.getDefaultAlgorithm());

        TrustManager[] tms = tmp.createTrustManagers();
        validateTrustManagers(tms);
    }

    @Test
    public void testExplicitProvider() throws Exception {
        TrustManagersParameters tmp = this.createMinimalTrustManagersParameters();
        tmp.setProvider(TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).getProvider().getName());

        TrustManager[] tms = tmp.createTrustManagers();
        validateTrustManagers(tms);
    }

    @Test
    public void testInvalidExplicitAlgorithm() throws Exception {
        TrustManagersParameters tmp = this.createMinimalTrustManagersParameters();
        tmp.setAlgorithm("dsfsdfsdfdsfdsF");

        try {
            tmp.createTrustManagers();
            fail();
        } catch (NoSuchAlgorithmException e) {
            // expected
        }
    }

    @Test
    public void testInvalidExplicitProvider() throws Exception {
        TrustManagersParameters tmp = this.createMinimalTrustManagersParameters();
        tmp.setProvider("dsfsdfsdfdsfdsF");

        try {
            tmp.createTrustManagers();
            fail();
        } catch (NoSuchProviderException e) {
            // expected
        }
    }

    protected void validateTrustManagers(TrustManager[] tms) {
        assertEquals(1, tms.length);
        boolean b = tms[0] instanceof X509TrustManager;
        assertTrue(b);
        X509TrustManager tm = (X509TrustManager) tms[0];
        assertNotNull(tm.getAcceptedIssuers());
    }
}
