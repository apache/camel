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
import java.util.Arrays;

import com.google.gson.Gson;
import io.nessus.aries.AgentConfiguration;
import io.nessus.aries.util.AttachmentKey;
import io.nessus.aries.util.AttachmentSupport;
import io.nessus.aries.wallet.NessusWallet;
import io.nessus.aries.wallet.WalletBuilder;
import io.nessus.aries.wallet.WalletRegistry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.hyperledger.aries.api.connection.ConnectionRecord;
import org.hyperledger.aries.api.ledger.IndyLedgerRoles;
import org.hyperledger.aries.config.GsonConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractCamelAriesTest extends CamelTestSupport {

    static final String GOVERNMENT = "Government";
    static final String FABER = "Faber";
    static final String ACME = "Acme";
    static final String THRIFT = "Thrift";
    static final String ALICE = "Alice";

    static final Gson GSON = GsonConfig.defaultConfig();

    protected final Logger log = LoggerFactory.getLogger(getClass());

    private AttachmentContext attcontext = new AttachmentContext();

    public HyperledgerAriesComponent getAriesComponent() {
        return context.getComponent("hyperledger-aries", HyperledgerAriesComponent.class);
    }

    public AgentConfiguration getAgentConfiguration() {
        return getAriesComponent().getAgentConfiguration();
    }

    public void setRemoveWalletsOnShutdown(boolean flag) {
        getAriesComponent().setRemoveWalletsOnShutdown(flag);
    }

    public NessusWallet assertWallet(String walletName) {
        return getAriesComponent().assertWallet(walletName);
    }

    public NessusWallet getWallet(String walletName) {
        return getAriesComponent().getWallet(walletName);
    }

    public AttachmentContext getAttachmentContext() {
        return attcontext;
    }

    public void logSection(String title) {
        int len = 119 - title.length();
        char[] tail = new char[len];
        Arrays.fill(tail, '=');
        log.info("{} {}", title, String.valueOf(tail));
    }

    public NessusWallet onboardWallet(String walletName) throws IOException {
        return onboardWallet(walletName, null);
    }

    public NessusWallet onboardWallet(String walletName, IndyLedgerRoles role) throws IOException {

        logSection("Onboard " + walletName);

        WalletRegistry walletRegistry = getAriesComponent().getWalletRegistry();

        NessusWallet walletRecord = new WalletBuilder(walletName)
                .walletRegistry(walletRegistry)
                .selfRegisterNym(role != null)
                .ledgerRole(role)
                .build();

        return walletRecord;
    }

    // Attachment Support ===============================================================

    public static class AttachmentContext extends AttachmentSupport {

        public ConnectionRecord getConnection(String inviter, String invitee) {
            return getAttachment(inviter + invitee + "Connection", ConnectionRecord.class);
        }

        public <T> T getAttachment(String name, Class<T> type) {
            return getAttachment(new AttachmentKey<>(name, type));
        }

        public <T> T putAttachment(String name, Class<T> type, T obj) {
            return putAttachment(new AttachmentKey<T>(name, type), obj);
        }

        public @SuppressWarnings("unchecked") <T> T putAttachment(String name, T obj) {
            return putAttachment(new AttachmentKey<T>(name, (Class<T>) obj.getClass()), obj);
        }
    }
}
