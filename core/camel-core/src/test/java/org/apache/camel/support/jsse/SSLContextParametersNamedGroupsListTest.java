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

import java.lang.reflect.Method;
import java.util.List;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLParameters;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Blank, duplicate and not trimmed values in the named groups and signature schemes lists must not make the configured
 * list be ignored.
 */
public class SSLContextParametersNamedGroupsListTest {

    private static List<String> get(SSLParameters params, String name) throws Exception {
        Method method = SSLParameters.class.getMethod(name);
        return List.of((String[]) method.invoke(params));
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testNamedGroups() throws Exception {
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(List.of("secp384r1", "secp384r1", "", " x25519"));
        SSLContextParameters scp = new SSLContextParameters();
        scp.setNamedGroups(ngp);

        SSLEngine engine = scp.createSSLContext(null).createSSLEngine();
        assertEquals(List.of("secp384r1", "x25519"), get(engine.getSSLParameters(), "getNamedGroups"));
    }

    @Test
    @EnabledForJreRange(min = JRE.JAVA_21)
    public void testSignatureSchemes() throws Exception {
        SignatureSchemesParameters ssp = new SignatureSchemesParameters();
        ssp.setSignatureScheme(List.of("ecdsa_secp256r1_sha256", " ecdsa_secp256r1_sha256", "", "rsa_pss_rsae_sha256"));
        SSLContextParameters scp = new SSLContextParameters();
        scp.setSignatureSchemes(ssp);

        SSLEngine engine = scp.createSSLContext(null).createSSLEngine();
        assertEquals(List.of("ecdsa_secp256r1_sha256", "rsa_pss_rsae_sha256"),
                get(engine.getSSLParameters(), "getSignatureSchemes"));
    }
}
