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
package org.apache.camel.component.aries;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import io.nessus.aries.AgentConfiguration;
import io.nessus.aries.AriesClientFactory;
import io.nessus.aries.util.AssertArg;
import io.nessus.aries.util.AssertState;
import io.nessus.aries.wallet.DefaultEventHandler;
import io.nessus.aries.wallet.NessusWallet;
import io.nessus.aries.wallet.WalletRegistry;
import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.hyperledger.aries.AriesClient;
import org.hyperledger.aries.AriesWebSocketClient;
import org.hyperledger.aries.api.multitenancy.WalletRecord;
import org.hyperledger.aries.api.multitenancy.WalletRecord.WalletSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component("hyperledger-aries")
public class HyperledgerAriesComponent extends DefaultComponent {

    private static final Logger LOG = LoggerFactory.getLogger(HyperledgerAriesComponent.class);

    private final WalletRegistry walletRegistry = new WalletRegistry();
    private AgentConfiguration agentConfig;

    @Metadata(description = "Remove wallets from the Agent on shutdown")
    private boolean removeWalletsOnShutdown;

    private AriesClient adminClient;
    private AriesWebSocketClient adminWebSocketClient;

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        HyperledgerAriesConfiguration configuration = new HyperledgerAriesConfiguration();
        configuration.setWalletName(remaining);

        HyperledgerAriesEndpoint endpoint = new HyperledgerAriesEndpoint(uri, this, configuration);
        setProperties(endpoint, parameters);

        return endpoint;
    }

    public boolean isRemoveWalletsOnShutdown() {
        return removeWalletsOnShutdown;
    }

    public void setRemoveWalletsOnShutdown(boolean removeWalletsOnShutdown) {
        this.removeWalletsOnShutdown = removeWalletsOnShutdown;
    }

    @Override
    protected void doShutdown() throws Exception {
        if (adminWebSocketClient != null) {
            adminWebSocketClient.close();
        }
        if (removeWalletsOnShutdown) {
            for (NessusWallet wallet : walletRegistry.getWallets()) {
                wallet.closeAndRemove();
            }
        }
    }

    public AgentConfiguration getAgentConfiguration() {
        if (agentConfig == null) {
            agentConfig = AgentConfiguration.defaultConfiguration();
        }
        return agentConfig;
    }

    public void setAgentConfiguration(AgentConfiguration agentConfig) {
        this.agentConfig = agentConfig;
    }

    public WalletRegistry getWalletRegistry() {
        return walletRegistry;
    }

    public List<String> getWalletNames() {
        return walletRegistry.getWalletNames();
    }

    public void addWallet(NessusWallet wallet) {
        walletRegistry.putWallet(wallet);
    }

    public NessusWallet getWallet(String walletName) {
        NessusWallet wallet = walletRegistry.getWalletByName(walletName);
        return wallet;
    }

    public NessusWallet assertWallet(String walletName) {
        NessusWallet wallet = getWallet(walletName);
        AssertState.notNull(wallet, "Cannot obtain wallet for: " + walletName);
        return wallet;
    }

    public AriesClient adminClient() {
        if (adminClient == null) {
            AgentConfiguration agentConfig = getAgentConfiguration();
            adminClient = AriesClientFactory.adminClient(agentConfig);
        }
        return adminClient;
    }

    public AriesWebSocketClient adminWebSocketClient() {
        if (adminWebSocketClient == null) {
            WalletSettings settings = new WalletSettings();
            settings.setWalletName("admin");
            WalletRecord adminRecord = NessusWallet.builder()
                    .walletId("00000000")
                    .settings(settings)
                    .build();
            NessusWallet adminWallet = NessusWallet.build(adminRecord);
            String webSocketUrl = agentConfig.getWebSocketUrl();
            LOG.info("WebSocket connecting to {} ...", webSocketUrl);
            adminWebSocketClient = AriesWebSocketClient.builder()
                    .url(webSocketUrl)
                    .handler(new DefaultEventHandler(adminWallet, walletRegistry))
                    .apiKey(agentConfig.getApiKey())
                    .build();
        }
        return adminWebSocketClient;
    }

    public AriesClient createClient(WalletRecord walletRecord) throws IOException {
        AssertArg.notNull(walletRecord, "No WalletRecord");
        AgentConfiguration agentConfig = getAgentConfiguration();
        return AriesClientFactory.createClient(walletRecord, agentConfig);
    }

    public AriesWebSocketClient getWebSocketClient(String walletName) {
        NessusWallet wallet = assertWallet(walletName);
        return wallet.getWebSocketClient();
    }
}
