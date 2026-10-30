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
package org.apache.camel.component.mtconnect;

import java.util.List;

import javax.net.ssl.SSLHandshakeException;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.jsse.KeyStoreParameters;
import org.apache.camel.support.jsse.SSLContextParameters;
import org.apache.camel.support.jsse.TrustManagersParameters;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** https agents whose certificate comes from a private certificate authority. */
class MTConnectTlsTest extends MTConnectTestSupport {

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("testCa", trustingTheTestCa());
    }

    @Test
    void theConsumerReadsAnAgentWhoseAuthorityTheSslContextParametersTrust() throws Exception {
        StubAgent agent = httpsAgent(StubDialect.V2_5);

        ReceivedMessages received = streaming(agent, "Trial", "sslContextParameters=#testCa");
        long next = agent.set("x_load", "1");

        awaitAgent().untilAsserted(() -> assertThat(received.matching(ReceivedMessages.Received::sample))
                .extracting(ReceivedMessages.Received::sequence).containsExactly(next));
    }

    @Test
    void anUntrustedCertificateIsAnOutageForTheConsumer() throws Exception {
        StubAgent agent = httpsAgent(StubDialect.V2_5);
        agent.closeConnectionAfterStreams();
        ReceivedMessages received = streaming(agent, "Trial", "sslContextParameters=#testCa");

        // the agent ends the stream the normal way; the new connection meets the untrusted certificate
        agent.certificate("tls/untrusted.p12");
        agent.closeStreams();

        awaitAgent().untilAsserted(() -> assertThat(received.matching(m -> m.bad() && m.sequence() == null))
                .hasSize(11)
                .allSatisfy(m -> assertThat(m.headers())
                        .containsEntry(MTConnectConstants.REASON, MTConnectConstants.REASON_UNREACHABLE)
                        .hasEntrySatisfying(MTConnectConstants.ERROR,
                                error -> assertThat(error.toString()).startsWith("SSLHandshakeException"))));
        assertThat(agent.requests()).filteredOn(request -> request.contains("/sample")).hasSize(1);
    }

    @Test
    void theProducerReadsATrustedAgentAndFailsOnAnUntrustedOne() throws Exception {
        StubAgent agent = httpsAgent(StubDialect.V2_5);

        Exchange trusted = template.send("mtconnect:" + agent.url() + "?device=Trial&operation=probe"
                                         + "&sslContextParameters=#testCa",
                exchange -> {
                });
        Exchange untrusted = template.send("mtconnect:" + agent.url() + "?device=Trial&operation=probe", exchange -> {
        });

        assertThat(trusted.getException()).isNull();
        assertThat(trusted.getMessage().getBody(String.class)).contains("<MTConnectDevices");
        assertThat(untrusted.getException()).isInstanceOf(CamelExchangeException.class)
                .hasCauseInstanceOf(SSLHandshakeException.class);
    }

    @Test
    void theGlobalSslContextParametersApplyWhenTheComponentUsesThem() throws Exception {
        StubAgent agent = httpsAgent(StubDialect.V2_5);
        context.setSSLContextParameters(trustingTheTestCa());
        String uri = "mtconnect:" + agent.url() + "?device=Trial&operation=current";

        Exchange withoutGlobal = template.send(uri, exchange -> {
        });
        context.getComponent("mtconnect", MTConnectComponent.class).setUseGlobalSslContextParameters(true);
        Exchange withGlobal = template.send(uri + "&count=5", exchange -> {
        });

        assertThat(withoutGlobal.getException()).hasCauseInstanceOf(SSLHandshakeException.class);
        assertThat(withGlobal.getException()).isNull();
        assertThat(withGlobal.getMessage().getBody(List.class)).hasSize(11);
    }

    private static SSLContextParameters trustingTheTestCa() {
        KeyStoreParameters trustStore = new KeyStoreParameters();
        trustStore.setResource("tls/truststore.p12");
        trustStore.setType("PKCS12");
        trustStore.setPassword(new String(StubAgent.PASSWORD));
        TrustManagersParameters trustManagers = new TrustManagersParameters();
        trustManagers.setKeyStore(trustStore);
        SSLContextParameters parameters = new SSLContextParameters();
        parameters.setTrustManagers(trustManagers);
        return parameters;
    }
}
