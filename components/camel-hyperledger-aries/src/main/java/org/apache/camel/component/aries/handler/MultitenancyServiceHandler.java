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
package org.apache.camel.component.aries.handler;

import io.nessus.aries.util.AssertState;
import io.nessus.aries.wallet.NessusWallet;
import io.nessus.aries.wallet.WalletRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.component.aries.HyperledgerAriesEndpoint;
import org.apache.camel.component.aries.UnsupportedServiceException;
import org.hyperledger.acy_py.generated.model.DID;
import org.hyperledger.acy_py.generated.model.DIDEndpoint;
import org.hyperledger.acy_py.generated.model.TxnOrRegisterLedgerNymResponse;
import org.hyperledger.aries.AriesClient;
import org.hyperledger.aries.api.ledger.IndyLedgerRoles;
import org.hyperledger.aries.api.ledger.RegisterNymFilter;
import org.hyperledger.aries.api.multitenancy.CreateWalletRequest;
import org.hyperledger.aries.api.multitenancy.WalletRecord;
import org.hyperledger.aries.api.wallet.WalletDIDCreate;

import static io.nessus.aries.wallet.WalletBuilder.selfRegisterWithDid;
import static org.apache.camel.component.aries.Constants.HEADER_MULTITENANCY_LEDGER_ROLE;
import static org.apache.camel.component.aries.Constants.HEADER_MULTITENANCY_SELF_REGISTER_NYM;
import static org.apache.camel.component.aries.Constants.HEADER_MULTITENANCY_TRUSTEE_WALLET;

public class MultitenancyServiceHandler extends AbstractServiceHandler {

    public MultitenancyServiceHandler(HyperledgerAriesEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange, String service) throws Exception {

        if (service.equals("/multitenancy/wallet")) {
            CreateWalletRequest walletRequest = assertBody(exchange, CreateWalletRequest.class);
            String walletName = walletRequest.getWalletName();

            boolean selfRegister = maybeHeader(exchange, HEADER_MULTITENANCY_SELF_REGISTER_NYM, boolean.class);
            IndyLedgerRoles ledgerRole = maybeHeader(exchange, HEADER_MULTITENANCY_LEDGER_ROLE, IndyLedgerRoles.class);
            String trusteeName = maybeHeader(exchange, HEADER_MULTITENANCY_TRUSTEE_WALLET, String.class);

            WalletRegistry walletRegistry = getComponent().getWalletRegistry();
            WalletRecord walletRecord = adminClient().multitenancyWalletCreate(walletRequest).get();
            NessusWallet wallet = NessusWallet.build(walletRecord).withWalletRegistry(walletRegistry);
            getComponent().addWallet(wallet);

            String walletId = wallet.getWalletId();
            log.info("{}: [{}] {}", walletName, walletId, wallet);

            if (ledgerRole != null) {

                AssertState.isTrue(selfRegister || trusteeName != null,
                        "LedgerRole " + ledgerRole + " requires selfRegister or trusteeWallet");

                // Create a local DID for the wallet
                AriesClient client = getComponent().createClient(walletName);
                DID did = client.walletDidCreate(WalletDIDCreate.builder().build()).get();
                log.info("{}: {}", walletName, did);

                if (trusteeName != null) {

                    AriesClient trustee = getComponent().createClient(trusteeName);
                    TxnOrRegisterLedgerNymResponse nymResponse = trustee.ledgerRegisterNym(RegisterNymFilter.builder()
                            .did(did.getDid())
                            .verkey(did.getVerkey())
                            .role(ledgerRole)
                            .build()).get();
                    log.info("{} for {}: {}", trusteeName, walletName, nymResponse);
                } else if (selfRegister) {
                    // Register DID with the leder (out-of-band)
                    selfRegisterWithDid(walletName, did.getDid(), did.getVerkey(), ledgerRole);
                }

                // Set the public DID for the wallet
                client.walletDidPublic(did.getDid());
                wallet.setPublicDid(did);

                DIDEndpoint didEndpoint = client.walletGetDidEndpoint(did.getDid()).get();
                log.info("{}: {}", walletName, didEndpoint);
            }

            exchange.getIn().setBody(wallet);
        } else {
            throw new UnsupportedServiceException(service);
        }
    }
}
