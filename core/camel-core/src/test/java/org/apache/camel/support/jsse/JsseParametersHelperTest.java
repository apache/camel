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

import java.util.List;

import javax.net.ssl.SSLContext;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The options of {@link SSLContextParameters} that a component loses when it hands the key and trust material to a TLS
 * stack of its own instead of using the {@link SSLContext} of {@code createSSLContext}.
 */
public class JsseParametersHelperTest {

    @Test
    public void testNoEngineOnlyParameters() {
        SSLContextParameters scp = new SSLContextParameters();
        scp.setCipherSuites(new CipherSuitesParameters());
        scp.setSecureSocketProtocol("TLSv1.3");

        assertThat(JsseParametersHelper.engineOnlyParameters(scp)).isEmpty();
    }

    @Test
    public void testEngineOnlyParameters() {
        SSLContextParameters scp = new SSLContextParameters();
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(List.of("secp384r1"));
        scp.setNamedGroups(ngp);
        scp.setSignatureSchemesFilter(new FilterParameters());

        assertThat(JsseParametersHelper.engineOnlyParameters(scp)).containsExactly("namedGroups", "signatureSchemesFilter");

        scp.setNamedGroupsFilter(new FilterParameters());
        SignatureSchemesParameters ssp = new SignatureSchemesParameters();
        ssp.setSignatureScheme(List.of("ed25519"));
        scp.setSignatureSchemes(ssp);

        assertThat(JsseParametersHelper.engineOnlyParameters(scp))
                .containsExactly("namedGroups", "namedGroupsFilter", "signatureSchemes", "signatureSchemesFilter");
    }

    @Test
    public void testPostQuantumNamedGroupsOrder() {
        // the same order as createSSLContext applies: the hybrids, the classical fallbacks, then the rest of the JVM
        assertThat(JsseParametersHelper.postQuantumNamedGroups(
                new String[] { "x25519", "secp256r1", "SecP256r1MLKEM768", "X25519MLKEM768", "secp384r1", "ffdhe2048" }))
                .containsExactly("X25519MLKEM768", "SecP256r1MLKEM768", "x25519", "secp256r1", "secp384r1", "ffdhe2048");
    }

    @Test
    public void testPostQuantumNamedGroupsLeavesTheOrderAlone() {
        // already leads with a hybrid, such as the default of JDK 27 or a CNSA 2.0 order set by jdk.tls.namedGroups
        assertThat(JsseParametersHelper.postQuantumNamedGroups(new String[] { "SecP384r1MLKEM1024", "secp384r1" })).isNull();
        // no hybrid on offer
        assertThat(JsseParametersHelper.postQuantumNamedGroups(new String[] { "x25519", "secp256r1" })).isNull();
        assertThat(JsseParametersHelper.postQuantumNamedGroups((String[]) null)).isNull();
    }

    @Test
    public void testPostQuantumNamedGroupsOfConfiguredNamedGroups() {
        SSLContextParameters scp = new SSLContextParameters();
        NamedGroupsParameters ngp = new NamedGroupsParameters();
        ngp.setNamedGroup(List.of("x25519", "X25519MLKEM768"));
        scp.setNamedGroups(ngp);
        assertThat(JsseParametersHelper.postQuantumNamedGroups(scp)).isNull();

        scp = new SSLContextParameters();
        scp.setNamedGroupsFilter(new FilterParameters());
        assertThat(JsseParametersHelper.postQuantumNamedGroups(scp)).isNull();
    }

    @Test
    public void testPostQuantumNamedGroupsMatchCreateSSLContext() throws Exception {
        SSLContext plain = SSLContext.getInstance("TLS");
        plain.init(null, null, null);
        String[] jvmGroups = plain.getDefaultSSLParameters().getNamedGroups();

        SSLContextParameters scp = new SSLContextParameters();
        List<String> expected = JsseParametersHelper.postQuantumNamedGroups(scp);
        String[] applied = scp.createSSLContext(null).createSSLEngine().getSSLParameters().getNamedGroups();

        // whatever this JVM offers: the order the helper reports is the one createSSLContext applies, and when it
        // reports none, createSSLContext keeps the order of the JVM
        if (expected != null) {
            assertThat(applied).containsExactlyElementsOf(expected);
        } else {
            assertThat(applied).isEqualTo(jvmGroups);
        }
    }
}
