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

import io.nessus.aries.wallet.NessusWallet;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.DefaultEndpoint;
import org.hyperledger.aries.AriesClient;

@UriEndpoint(firstVersion = "3.19.0", scheme = "hyperledger-aries", title = "Hyperledger Aries", syntax = "aries:walletName",
             producerOnly = true, category = { Category.BLOCKCHAIN, Category.IDENTITY })
public class HyperledgerAriesEndpoint extends DefaultEndpoint {

    @UriParam
    private HyperledgerAriesConfiguration configuration;

    public HyperledgerAriesEndpoint(String uri, HyperledgerAriesComponent component,
                                    HyperledgerAriesConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public HyperledgerAriesComponent getComponent() {
        return (HyperledgerAriesComponent) super.getComponent();
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public Producer createProducer() throws Exception {
        return new HyperledgerAriesProducer(this);
    }

    public HyperledgerAriesConfiguration getConfiguration() {
        return configuration;
    }

    public String getWalletName() {
        return getConfiguration().getWalletName();
    }

    public NessusWallet getWallet() {
        return getComponent().getWallet(getWalletName());
    }

    public AriesClient createClient() throws IOException {
        String walletName = getWalletName();
        if (getWallet() == null && "admin".equals(walletName)) {
            return getComponent().adminClient();
        } else {
            return getComponent().createClient(walletName);
        }
    }
}
