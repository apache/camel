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
package org.apache.camel.component.netty;

import java.lang.reflect.Method;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.apache.camel.component.netty.ssl.SSLEngineFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class SSLEngineFactoryTest {

    @Test
    public void testApplyPqcNamedGroupsOnSupportedJdk() throws Exception {
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, null, null);
        SSLEngine engine = context.createSSLEngine();

        SSLEngineFactory.applyPqcNamedGroups(engine);

        // Verify named groups were reordered if JDK supports the API and PQC groups
        try {
            Method getNamedGroups = SSLParameters.class.getMethod("getNamedGroups");
            String[] groups = (String[]) getNamedGroups.invoke(engine.getSSLParameters());
            if (groups != null) {
                boolean pqcAvailable = false;
                for (String group : groups) {
                    if ("X25519MLKEM768".equals(group)) {
                        pqcAvailable = true;
                        break;
                    }
                }
                if (pqcAvailable) {
                    assertEquals("X25519MLKEM768", groups[0],
                            "PQC named group should be first when available");
                }
            }
        } catch (NoSuchMethodException e) {
            // JDK < 20 — test passes trivially
        }
    }

    @Test
    public void testApplyPqcNamedGroupsDoesNotThrow() throws Exception {
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, null, null);
        SSLEngine engine = context.createSSLEngine();

        // Must not throw on any JDK version
        SSLEngineFactory.applyPqcNamedGroups(engine);
    }

    @Test
    public void testApplyPqcNamedGroupsPreservesExistingParameters() throws Exception {
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        context.init(null, null, null);
        SSLEngine engine = context.createSSLEngine();

        // Set enabled protocols before applying PQC groups
        engine.setEnabledProtocols(new String[] { "TLSv1.3" });

        SSLEngineFactory.applyPqcNamedGroups(engine);

        // Verify enabledProtocols were not clobbered
        String[] protocols = engine.getEnabledProtocols();
        assertNotNull(protocols);
        assertEquals(1, protocols.length);
        assertEquals("TLSv1.3", protocols[0]);
    }

    @Test
    public void testSslProtocolIsTls13() throws Exception {
        SSLContext context = SSLContext.getInstance("TLSv1.3");
        assertNotNull(context);
        assertEquals("TLSv1.3", context.getProtocol());
    }
}
