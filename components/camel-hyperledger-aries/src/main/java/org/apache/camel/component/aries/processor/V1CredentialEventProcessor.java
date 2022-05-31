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
package org.apache.camel.component.aries.processor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.hyperledger.aries.AriesWebSocketClient;
import org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeRole;
import org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeState;
import org.hyperledger.aries.api.issue_credential_v1.V1CredentialExchange;

import static org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeRole.HOLDER;
import static org.hyperledger.aries.api.issue_credential_v1.CredentialExchangeRole.ISSUER;

public class V1CredentialEventProcessor extends AbstractAriesProcessor {

    private final String issuerName;
    private final String holderName;

    public V1CredentialEventProcessor(String issuerName, String holderName) {
        this.issuerName = issuerName;
        this.holderName = holderName;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        AriesWebSocketClient issuerEvents = getAriesComponent(exchange).getWebSocketClient(issuerName);
        AriesWebSocketClient holderEvents = getAriesComponent(exchange).getWebSocketClient(holderName);
        exchange.getIn().setHeader(V1CredentialEventProcessor.class.getSimpleName(), this);
        exchange.getIn().setHeader(getHeaderKey(ISSUER), issuerEvents);
        exchange.getIn().setHeader(getHeaderKey(HOLDER), holderEvents);
    }

    public static String getHeaderKey(CredentialExchangeRole role) {
        return role + AriesWebSocketClient.class.getSimpleName();
    }

    public static AriesWebSocketClient getWebSocketClient(Exchange exchange, CredentialExchangeRole role) {
        return exchange.getIn().getHeader(getHeaderKey(role), AriesWebSocketClient.class);
    }

    public static boolean awaitIssuerRequestReceived(Exchange exchange, long timeout, TimeUnit unit) {
        V1CredentialExchange credExchange = getWebSocketClient(exchange, ISSUER).credentialEx()
                .filter(cex -> cex.getState() == CredentialExchangeState.REQUEST_RECEIVED)
                .blockFirst(Duration.ofMillis(unit.toMillis(timeout)));
        exchange.getIn().setHeader(ISSUER + "CredentialExchange", credExchange);
        return true;
    }

    public static boolean awaitHolderOfferReceived(Exchange exchange, long timeout, TimeUnit unit) {
        V1CredentialExchange credExchange = getWebSocketClient(exchange, HOLDER).credentialEx()
                .filter(cex -> cex.getState() == CredentialExchangeState.OFFER_RECEIVED)
                .blockFirst(Duration.ofMillis(unit.toMillis(timeout)));
        exchange.getIn().setHeader(HOLDER + "CredentialExchange", credExchange);
        return true;
    }

    public static boolean awaitHolderCredentialReceived(Exchange exchange, long timeout, TimeUnit unit) {
        V1CredentialExchange credExchange = getWebSocketClient(exchange, HOLDER).credentialEx()
                .filter(cex -> cex.getState() == CredentialExchangeState.CREDENTIAL_RECEIVED)
                .blockFirst(Duration.ofMillis(unit.toMillis(timeout)));
        exchange.getIn().setHeader(HOLDER + "CredentialExchange", credExchange);
        return true;
    }

    public static boolean awaitHolderCredentialAcked(Exchange exchange, long timeout, TimeUnit unit) {
        V1CredentialExchange credExchange = getWebSocketClient(exchange, HOLDER).credentialEx()
                .filter(cex -> cex.getState() == CredentialExchangeState.CREDENTIAL_ACKED)
                .blockFirst(Duration.ofMillis(unit.toMillis(timeout)));
        exchange.getIn().setHeader(HOLDER + "CredentialExchange", credExchange);
        return true;
    }
}
